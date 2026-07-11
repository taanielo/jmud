package io.taanielo.jmud.core.trade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link TradeService}: proposal validation, offer staging, the anti-scam confirm
 * reset, and the per-tick auto-cancel guard.
 */
class TradeServiceTest {

    private static final Username ALICE = Username.of("Alice");
    private static final Username BOB = Username.of("Bob");
    private static final Username CAROL = Username.of("Carol");
    private static final RoomId ROOM_ONE = RoomId.of("room-one");
    private static final RoomId ROOM_TWO = RoomId.of("room-two");

    private final Map<Username, TradeParticipantStatus> statuses = new HashMap<>();
    private final Map<Username, List<String>> notifications = new HashMap<>();
    private TradeService service;

    @BeforeEach
    void setUp() {
        statuses.clear();
        notifications.clear();
        setStatus(ALICE, ROOM_ONE, false, false);
        setStatus(BOB, ROOM_ONE, false, false);
        setStatus(CAROL, ROOM_ONE, false, false);
        service = new TradeService(
            username -> statuses.getOrDefault(username, TradeParticipantStatus.OFFLINE),
            (username, message) -> notifications
                .computeIfAbsent(username, u -> new ArrayList<>())
                .add(message));
    }

    // ── Proposal validation ───────────────────────────────────────────

    @Test
    void proposeSucceedsForOnlineSameRoomTarget() {
        TradeService.TradeResult result = service.propose(ALICE, BOB);
        assertTrue(result.success());
        assertTrue(service.isTrading(ALICE));
        assertTrue(service.isTrading(BOB));
    }

    @Test
    void proposeFailsWhenTargetOffline() {
        statuses.remove(BOB);
        TradeService.TradeResult result = service.propose(ALICE, BOB);
        assertFalse(result.success());
        assertFalse(service.isTrading(ALICE));
    }

    @Test
    void proposeFailsWhenTargetInAnotherRoom() {
        setStatus(BOB, ROOM_TWO, false, false);
        TradeService.TradeResult result = service.propose(ALICE, BOB);
        assertFalse(result.success());
        assertFalse(service.isTrading(ALICE));
    }

    @Test
    void proposeFailsWhenTargetAlreadyTrading() {
        assertTrue(service.propose(ALICE, BOB).success());
        TradeService.TradeResult result = service.propose(CAROL, BOB);
        assertFalse(result.success());
    }

    @Test
    void proposeFailsForSelf() {
        assertFalse(service.propose(ALICE, ALICE).success());
    }

    // ── Accept / decline ──────────────────────────────────────────────

    @Test
    void acceptMakesSessionActive() {
        service.propose(ALICE, BOB);
        assertTrue(service.accept(BOB).success());
        assertTrue(service.session(BOB).orElseThrow().isAccepted());
    }

    @Test
    void proposerCannotAcceptOwnProposal() {
        service.propose(ALICE, BOB);
        assertFalse(service.accept(ALICE).success());
    }

    @Test
    void declineRemovesTheSession() {
        service.propose(ALICE, BOB);
        assertTrue(service.decline(BOB).success());
        assertFalse(service.isTrading(ALICE));
        assertFalse(service.isTrading(BOB));
    }

    // ── Offer staging & anti-scam reset ───────────────────────────────

    @Test
    void addingItemRequiresAnAcceptedSession() {
        service.propose(ALICE, BOB);
        TradeService.TradeResult result = service.addItem(ALICE, torch());
        assertFalse(result.success());
    }

    @Test
    void changingOfferAfterConfirmResetsBothConfirmFlags() {
        service.propose(ALICE, BOB);
        service.accept(BOB);
        service.addItem(ALICE, torch());
        assertTrue(service.confirm(ALICE).success());
        assertTrue(service.confirm(BOB).success());
        assertTrue(service.session(ALICE).orElseThrow().bothConfirmed());

        service.addGold(BOB, 5);

        TradeSession session = service.session(ALICE).orElseThrow();
        assertFalse(session.bothConfirmed());
        assertFalse(session.hasConfirmed(ALICE));
        assertFalse(session.hasConfirmed(BOB));
    }

    @Test
    void removeItemTakesItemBackOutOfOffer() {
        service.propose(ALICE, BOB);
        service.accept(BOB);
        Item torch = torch();
        service.addItem(ALICE, torch);
        assertTrue(service.removeItem(ALICE, "torch").success());
        assertTrue(service.session(ALICE).orElseThrow().itemsOf(ALICE).isEmpty());
    }

    @Test
    void addGoldRejectsNonPositiveAmounts() {
        service.propose(ALICE, BOB);
        service.accept(BOB);
        assertFalse(service.addGold(ALICE, 0).success());
    }

    @Test
    void cancelRemovesTheSession() {
        service.propose(ALICE, BOB);
        service.accept(BOB);
        assertTrue(service.cancel(ALICE).success());
        assertFalse(service.isTrading(ALICE));
        assertFalse(service.isTrading(BOB));
    }

    // ── Auto-cancel guard ─────────────────────────────────────────────

    @Test
    void tickCancelsWhenAParticipantGoesOffline() {
        service.propose(ALICE, BOB);
        service.accept(BOB);
        statuses.remove(BOB); // Bob disconnected / linkdead

        service.tick();

        assertFalse(service.isTrading(ALICE));
        assertFalse(service.isTrading(BOB));
        assertNotNull(notifications.get(ALICE));
        assertTrue(notifications.get(ALICE).getLast().toLowerCase(Locale.ROOT).contains("cancel"));
    }

    @Test
    void tickCancelsWhenParticipantsSplitUp() {
        service.propose(ALICE, BOB);
        service.accept(BOB);
        setStatus(BOB, ROOM_TWO, false, false); // Bob left the room

        service.tick();

        assertFalse(service.isTrading(ALICE));
        assertTrue(notifications.containsKey(ALICE));
        assertTrue(notifications.containsKey(BOB));
    }

    @Test
    void tickCancelsWhenAParticipantEntersCombat() {
        service.propose(ALICE, BOB);
        service.accept(BOB);
        setStatus(ALICE, ROOM_ONE, false, true); // Alice entered combat

        service.tick();

        assertFalse(service.isTrading(ALICE));
        assertTrue(notifications.containsKey(ALICE));
        assertTrue(notifications.containsKey(BOB));
    }

    @Test
    void tickCancelsWhenAParticipantDies() {
        service.propose(ALICE, BOB);
        service.accept(BOB);
        setStatus(BOB, ROOM_ONE, true, false); // Bob died

        service.tick();

        assertFalse(service.isTrading(ALICE));
        assertTrue(notifications.containsKey(ALICE));
    }

    @Test
    void tickLeavesAHealthySessionInPlace() {
        service.propose(ALICE, BOB);
        service.accept(BOB);

        service.tick();

        assertTrue(service.isTrading(ALICE));
        assertTrue(service.isTrading(BOB));
        assertTrue(notifications.isEmpty());
    }

    private void setStatus(Username user, RoomId room, boolean dead, boolean inCombat) {
        statuses.put(user, new TradeParticipantStatus(true, room, dead, inCombat));
    }

    private static Item torch() {
        return Item.builder(ItemId.of("torch"), "Torch", "A warm torch.", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.WEAPON)
            .weight(1)
            .value(5)
            .build();
    }
}
