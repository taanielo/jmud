package io.taanielo.jmud.core.party;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.party.PartyService.PartyResult;
import io.taanielo.jmud.core.world.RoomId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyServiceTest {

    private PartyService partyService;

    private static final Username ALICE = Username.of("Alice");
    private static final Username BOB   = Username.of("Bob");
    private static final Username CAROL = Username.of("Carol");
    private static final RoomId   ROOM1 = RoomId.of("room-1");
    private static final RoomId   ROOM2 = RoomId.of("room-2");

    @BeforeEach
    void setUp() {
        partyService = new PartyService();
    }

    // ── FORM ──────────────────────────────────────────────────────────

    @Test
    void form_createsPartyWithLeader() {
        PartyResult result = partyService.form(ALICE);

        assertTrue(result.success());
        Optional<Party> party = partyService.findParty(ALICE);
        assertTrue(party.isPresent());
        assertEquals(ALICE, party.get().leaderId());
        assertEquals(List.of(ALICE), party.get().memberIds());
    }

    @Test
    void form_failsWhenAlreadyInParty() {
        partyService.form(ALICE);

        PartyResult result = partyService.form(ALICE);

        assertFalse(result.success());
        assertTrue(result.message().contains("already in a party"));
    }

    // ── INVITE / ACCEPT ───────────────────────────────────────────────

    @Test
    void inviteAndAccept_addsInviteeToParty() {
        partyService.form(ALICE);

        PartyResult inviteResult = partyService.invite(ALICE, BOB, true);
        assertTrue(inviteResult.success());

        PartyResult acceptResult = partyService.accept(BOB);
        assertTrue(acceptResult.success());

        Optional<Party> party = partyService.findParty(BOB);
        assertTrue(party.isPresent());
        assertEquals(List.of(ALICE, BOB), party.get().memberIds());
        assertEquals(ALICE, party.get().leaderId());
    }

    @Test
    void invite_failsWhenInviteeIsOffline() {
        partyService.form(ALICE);

        PartyResult result = partyService.invite(ALICE, BOB, false);

        assertFalse(result.success());
        assertTrue(result.message().contains("not online"));
    }

    @Test
    void invite_failsWhenInviterIsNotLeader() {
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);

        // BOB is a member but not the leader
        PartyResult result = partyService.invite(BOB, CAROL, true);

        assertFalse(result.success());
        assertTrue(result.message().contains("leader"));
    }

    @Test
    void invite_failsWhenInviterHasNoParty() {
        PartyResult result = partyService.invite(ALICE, BOB, true);

        assertFalse(result.success());
        assertTrue(result.message().contains("not in a party"));
    }

    @Test
    void accept_failsWhenNoPendingInvite() {
        PartyResult result = partyService.accept(BOB);

        assertFalse(result.success());
        assertTrue(result.message().contains("no pending"));
    }

    // ── DECLINE ───────────────────────────────────────────────────────

    @Test
    void decline_removesPendingInvite() {
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);

        PartyResult result = partyService.decline(BOB);

        assertTrue(result.success());
        assertFalse(partyService.findParty(BOB).isPresent());
        assertFalse(partyService.getPendingInviter(BOB).isPresent());
    }

    @Test
    void decline_failsWhenNoPendingInvite() {
        PartyResult result = partyService.decline(BOB);

        assertFalse(result.success());
    }

    // ── LEAVE ─────────────────────────────────────────────────────────

    @Test
    void leave_removesPlayerFromParty() {
        // With 3 members: BOB leaving leaves ALICE + CAROL, so party survives.
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);
        partyService.invite(ALICE, CAROL, true);
        partyService.accept(CAROL);

        PartyResult result = partyService.leave(BOB);

        assertTrue(result.success());
        assertFalse(partyService.findParty(BOB).isPresent());
        Optional<Party> aliceParty = partyService.findParty(ALICE);
        assertTrue(aliceParty.isPresent());
        assertTrue(aliceParty.get().memberIds().contains(ALICE));
        assertTrue(aliceParty.get().memberIds().contains(CAROL));
        assertFalse(aliceParty.get().memberIds().contains(BOB));
    }

    @Test
    void leave_disbandWhenOnlyOneRemainingAfterLeave() {
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);

        // Alice leaves; only Bob remains → disband
        PartyResult result = partyService.leave(ALICE);

        assertTrue(result.success());
        assertTrue(result.message().contains("disbanded"));
        assertFalse(partyService.findParty(ALICE).isPresent());
        assertFalse(partyService.findParty(BOB).isPresent());
    }

    @Test
    void leave_leadershipTransferredWhenLeaderLeaves() {
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);
        partyService.invite(ALICE, CAROL, true);
        partyService.accept(CAROL);

        // Alice (leader) leaves; Bob should become leader
        partyService.leave(ALICE);

        Optional<Party> party = partyService.findParty(BOB);
        assertTrue(party.isPresent());
        assertEquals(BOB, party.get().leaderId());
        assertTrue(party.get().memberIds().contains(BOB));
        assertTrue(party.get().memberIds().contains(CAROL));
        assertFalse(party.get().memberIds().contains(ALICE));
    }

    @Test
    void leave_failsWhenNotInParty() {
        PartyResult result = partyService.leave(ALICE);

        assertFalse(result.success());
        assertTrue(result.message().contains("not in a party"));
    }

    // ── DISBAND ───────────────────────────────────────────────────────

    @Test
    void disband_removesAllMembersAndDestroyParty() {
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);

        PartyResult result = partyService.disband(ALICE);

        assertTrue(result.success());
        assertFalse(partyService.findParty(ALICE).isPresent());
        assertFalse(partyService.findParty(BOB).isPresent());
    }

    @Test
    void disband_failsWhenCallerIsNotLeader() {
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);

        PartyResult result = partyService.disband(BOB);

        assertFalse(result.success());
        assertTrue(result.message().contains("leader"));
    }

    @Test
    void disband_failsWhenNotInParty() {
        PartyResult result = partyService.disband(ALICE);

        assertFalse(result.success());
    }

    // ── XP SPLIT (getPartyMembersInRoom) ──────────────────────────────

    @Test
    void getPartyMembersInRoom_soloPlayer_returnsSelf() {
        List<Username> recipients = partyService.getPartyMembersInRoom(
            ALICE, ROOM1, u -> Optional.of(ROOM1));

        assertEquals(List.of(ALICE), recipients);
    }

    @Test
    void getPartyMembersInRoom_allMembersInRoom_returnsAll() {
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);
        partyService.invite(ALICE, CAROL, true);
        partyService.accept(CAROL);

        List<Username> recipients = partyService.getPartyMembersInRoom(
            ALICE, ROOM1, u -> Optional.of(ROOM1));

        assertEquals(3, recipients.size());
        assertTrue(recipients.contains(ALICE));
        assertTrue(recipients.contains(BOB));
        assertTrue(recipients.contains(CAROL));
    }

    @Test
    void getPartyMembersInRoom_onlyMembersInSameRoom() {
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);
        partyService.invite(ALICE, CAROL, true);
        partyService.accept(CAROL);

        // CAROL is in a different room
        List<Username> recipients = partyService.getPartyMembersInRoom(
            ALICE, ROOM1, u -> u.equals(CAROL) ? Optional.of(ROOM2) : Optional.of(ROOM1));

        assertEquals(2, recipients.size());
        assertTrue(recipients.contains(ALICE));
        assertTrue(recipients.contains(BOB));
        assertFalse(recipients.contains(CAROL));
    }

    @Test
    void xpSplit_twoMembersInRoom_halvesXp() {
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);

        List<Username> recipients = partyService.getPartyMembersInRoom(
            ALICE, ROOM1, u -> Optional.of(ROOM1));

        int mobXp = 100;
        int xpPerMember = (int) Math.floor((double) mobXp / Math.max(1, recipients.size()));

        assertEquals(2, recipients.size());
        assertEquals(50, xpPerMember);
    }

    @Test
    void xpSplit_threeMembersInRoom_dividesXp() {
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);
        partyService.invite(ALICE, CAROL, true);
        partyService.accept(CAROL);

        List<Username> recipients = partyService.getPartyMembersInRoom(
            ALICE, ROOM1, u -> Optional.of(ROOM1));

        int mobXp = 100;
        int xpPerMember = (int) Math.floor((double) mobXp / Math.max(1, recipients.size()));

        assertEquals(3, recipients.size());
        assertEquals(33, xpPerMember); // floor(100/3)
    }

    // ── getOtherMembers ───────────────────────────────────────────────

    @Test
    void getOtherMembers_returnsAllExceptSelf() {
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);

        List<Username> others = partyService.getOtherMembers(ALICE);

        assertEquals(List.of(BOB), others);
    }

    @Test
    void getOtherMembers_noParty_returnsEmpty() {
        List<Username> others = partyService.getOtherMembers(ALICE);

        assertTrue(others.isEmpty());
    }
}
