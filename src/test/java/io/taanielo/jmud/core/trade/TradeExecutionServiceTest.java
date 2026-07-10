package io.taanielo.jmud.core.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;

/**
 * Unit tests for {@link TradeExecutionService}, the pure atomic swap.
 */
class TradeExecutionServiceTest {

    private static final Username ALICE = Username.of("Alice");
    private static final Username BOB = Username.of("Bob");
    private static final Predicate<Player> NEVER_OVERBURDENED = player -> false;

    private final TradeExecutionService service = new TradeExecutionService();

    @Test
    void swapsItemsAndGoldAtomically() {
        Item torch = torch();
        Item sword = sword();
        Player alice = player("Alice").withGold(100).addItem(torch);
        Player bob = player("Bob").withGold(20).addItem(sword);

        TradeSession session = new TradeSession(ALICE, BOB);
        session.accept();
        session.addItem(ALICE, torch);
        session.addGold(ALICE, 30);
        session.addItem(BOB, sword);

        TradeExecutionService.TradeExecutionResult result =
            service.execute(alice, bob, session, NEVER_OVERBURDENED);

        assertTrue(result.success());
        Player updatedAlice = result.updatedProposer();
        Player updatedBob = result.updatedTarget();
        assertNotNull(updatedAlice);
        assertNotNull(updatedBob);
        assertEquals(70, updatedAlice.getGold());
        assertEquals(50, updatedBob.getGold());
        assertTrue(updatedAlice.getInventory().stream().anyMatch(i -> i.getId().equals(sword.getId())));
        assertFalse(updatedAlice.getInventory().stream().anyMatch(i -> i.getId().equals(torch.getId())));
        assertTrue(updatedBob.getInventory().stream().anyMatch(i -> i.getId().equals(torch.getId())));
        assertFalse(updatedBob.getInventory().stream().anyMatch(i -> i.getId().equals(sword.getId())));
        assertNotNull(result.proposerSummary());
        assertNotNull(result.targetSummary());
    }

    @Test
    void unequipsWornItemsBeforeSwapping() {
        Item torch = torch();
        Player alice = player("Alice").addItem(torch);
        alice = alice.withEquipment(alice.getEquipment().equip(EquipmentSlot.WEAPON, torch.getId()));
        Player bob = player("Bob");

        TradeSession session = new TradeSession(ALICE, BOB);
        session.accept();
        session.addItem(ALICE, torch);

        TradeExecutionService.TradeExecutionResult result =
            service.execute(alice, bob, session, NEVER_OVERBURDENED);

        assertTrue(result.success());
        assertFalse(result.updatedProposer().getEquipment().isEquipped(torch.getId()));
        assertFalse(result.updatedProposer().getInventory().stream()
            .anyMatch(i -> i.getId().equals(torch.getId())));
        assertTrue(result.updatedTarget().getInventory().stream()
            .anyMatch(i -> i.getId().equals(torch.getId())));
    }

    @Test
    void rejectsWhenGoldNoLongerCovered() {
        Player alice = player("Alice").withGold(10);
        Player bob = player("Bob");

        TradeSession session = new TradeSession(ALICE, BOB);
        session.accept();
        session.addGold(ALICE, 50);

        TradeExecutionService.TradeExecutionResult result =
            service.execute(alice, bob, session, NEVER_OVERBURDENED);

        assertFalse(result.success());
        assertNotNull(result.error());
        assertTrue(result.error().contains("Alice"));
    }

    @Test
    void rejectsWhenOfferedItemNoLongerHeld() {
        Item torch = torch();
        Player alice = player("Alice"); // never actually carries the torch
        Player bob = player("Bob");

        TradeSession session = new TradeSession(ALICE, BOB);
        session.accept();
        session.addItem(ALICE, torch);

        TradeExecutionService.TradeExecutionResult result =
            service.execute(alice, bob, session, NEVER_OVERBURDENED);

        assertFalse(result.success());
        assertNotNull(result.error());
    }

    @Test
    void rejectsWhenRecipientWouldBeOverburdened() {
        Item torch = torch();
        Player alice = player("Alice").addItem(torch);
        Player bob = player("Bob");

        TradeSession session = new TradeSession(ALICE, BOB);
        session.accept();
        session.addItem(ALICE, torch);

        TradeExecutionService.TradeExecutionResult result =
            service.execute(alice, bob, session, player -> player.getUsername().equals(BOB));

        assertFalse(result.success());
        assertNotNull(result.error());
        assertTrue(result.error().contains("Bob"));
    }

    private static Player player(String name) {
        return Player.of(User.of(Username.of(name), Password.hash("pw", 1000)), "prompt", false);
    }

    private static Item torch() {
        return Item.builder(ItemId.of("torch"), "Torch", "A warm torch.", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.WEAPON)
            .weight(1)
            .value(5)
            .build();
    }

    private static Item sword() {
        return Item.builder(ItemId.of("sword"), "Sword", "A sharp sword.", ItemAttributes.empty())
            .weight(2)
            .value(15)
            .build();
    }
}
