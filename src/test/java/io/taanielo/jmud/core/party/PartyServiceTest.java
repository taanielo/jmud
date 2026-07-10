package io.taanielo.jmud.core.party;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.party.PartyService.PartyResult;
import io.taanielo.jmud.core.world.RoomId;

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

    // ── LOOT MODE ─────────────────────────────────────────────────────

    @Test
    void lootMode_defaultsToFreeForSoloOrPartylessPlayer() {
        assertEquals(LootMode.FREE, partyService.lootMode(ALICE));

        partyService.form(ALICE);
        assertEquals(LootMode.FREE, partyService.lootMode(ALICE));
    }

    @Test
    void setLootMode_leaderCanSwitchToRoundRobin() {
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);

        PartyResult result = partyService.setLootMode(ALICE, LootMode.ROUND_ROBIN);

        assertTrue(result.success());
        assertEquals(LootMode.ROUND_ROBIN, partyService.lootMode(ALICE));
        // Mode is party-wide: every member sees it.
        assertEquals(LootMode.ROUND_ROBIN, partyService.lootMode(BOB));
    }

    @Test
    void setLootMode_rejectedForNonLeader() {
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);

        PartyResult result = partyService.setLootMode(BOB, LootMode.ROUND_ROBIN);

        assertFalse(result.success());
        assertTrue(result.message().contains("leader"));
        assertEquals(LootMode.FREE, partyService.lootMode(ALICE));
    }

    @Test
    void setLootMode_rejectedForPlayerNotInParty() {
        PartyResult result = partyService.setLootMode(ALICE, LootMode.ROUND_ROBIN);

        assertFalse(result.success());
        assertTrue(result.message().contains("not in a party"));
    }

    @Test
    void lootMode_preservedAcrossMembershipChanges() {
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);
        partyService.setLootMode(ALICE, LootMode.ROUND_ROBIN);

        // A new member joining must not reset the mode.
        partyService.invite(ALICE, CAROL, true);
        partyService.accept(CAROL);
        assertEquals(LootMode.ROUND_ROBIN, partyService.lootMode(CAROL));

        // Nor should a member leaving.
        partyService.leave(BOB);
        assertEquals(LootMode.ROUND_ROBIN, partyService.lootMode(ALICE));
    }

    // ── nextLootRecipient rotation ────────────────────────────────────

    @Test
    void nextLootRecipient_cyclesThroughMembersAcrossCalls() {
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);
        partyService.invite(ALICE, CAROL, true);
        partyService.accept(CAROL);
        partyService.setLootMode(ALICE, LootMode.ROUND_ROBIN);

        List<Username> eligible = List.of(ALICE, BOB, CAROL);
        // Everyone can always receive.
        assertEquals(Optional.of(ALICE), partyService.nextLootRecipient(ALICE, eligible, u -> true));
        assertEquals(Optional.of(BOB), partyService.nextLootRecipient(ALICE, eligible, u -> true));
        assertEquals(Optional.of(CAROL), partyService.nextLootRecipient(ALICE, eligible, u -> true));
        // Pointer wraps around for the next kill.
        assertEquals(Optional.of(ALICE), partyService.nextLootRecipient(ALICE, eligible, u -> true));
    }

    @Test
    void nextLootRecipient_skipsMembersThatCannotReceive() {
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);
        partyService.setLootMode(ALICE, LootMode.ROUND_ROBIN);

        List<Username> eligible = List.of(ALICE, BOB);
        // ALICE cannot receive → BOB gets it, and the pointer advances past BOB.
        assertEquals(Optional.of(BOB),
            partyService.nextLootRecipient(ALICE, eligible, u -> !u.equals(ALICE)));
        // Next call starts after BOB (wraps to ALICE); ALICE still can't, so BOB again.
        assertEquals(Optional.of(BOB),
            partyService.nextLootRecipient(ALICE, eligible, u -> !u.equals(ALICE)));
    }

    @Test
    void nextLootRecipient_emptyWhenNobodyCanReceive() {
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);
        partyService.setLootMode(ALICE, LootMode.ROUND_ROBIN);

        Optional<Username> recipient =
            partyService.nextLootRecipient(ALICE, List.of(ALICE, BOB), u -> false);

        assertTrue(recipient.isEmpty());
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

    // ── AUTO-FOLLOW ───────────────────────────────────────────────────

    private void formPartyOf(Username leader, Username member) {
        partyService.form(leader);
        partyService.invite(leader, member, true);
        partyService.accept(member);
    }

    @Test
    void follow_succeedsForSamePartyMember() {
        formPartyOf(ALICE, BOB);

        PartyResult result = partyService.follow(BOB, ALICE, true);

        assertTrue(result.success());
        assertTrue(result.message().contains("following Alice"));
        assertEquals(Optional.of(ALICE), partyService.leaderOf(BOB));
        assertEquals(List.of(BOB), partyService.followersOf(ALICE));
    }

    @Test
    void follow_failsWhenLeaderOffline() {
        formPartyOf(ALICE, BOB);

        PartyResult result = partyService.follow(BOB, ALICE, false);

        assertFalse(result.success());
        assertTrue(result.message().contains("not online"));
        assertTrue(partyService.leaderOf(BOB).isEmpty());
    }

    @Test
    void follow_failsWhenNotInSameParty() {
        partyService.form(ALICE);
        partyService.form(BOB);

        PartyResult result = partyService.follow(BOB, ALICE, true);

        assertFalse(result.success());
        assertTrue(result.message().contains("not in your party"));
    }

    @Test
    void follow_failsWhenFollowingSelf() {
        formPartyOf(ALICE, BOB);

        PartyResult result = partyService.follow(BOB, BOB, true);

        assertFalse(result.success());
    }

    @Test
    void unfollow_clearsRelationship() {
        formPartyOf(ALICE, BOB);
        partyService.follow(BOB, ALICE, true);

        PartyResult result = partyService.unfollow(BOB);

        assertTrue(result.success());
        assertTrue(partyService.leaderOf(BOB).isEmpty());
        assertTrue(partyService.followersOf(ALICE).isEmpty());
    }

    @Test
    void unfollow_failsWhenNotFollowing() {
        PartyResult result = partyService.unfollow(BOB);

        assertFalse(result.success());
    }

    @Test
    void inSameParty_reflectsMembership() {
        formPartyOf(ALICE, BOB);

        assertTrue(partyService.inSameParty(ALICE, BOB));
        assertFalse(partyService.inSameParty(ALICE, CAROL));
    }

    @Test
    void leave_clearsFollowInvolvingDepartingMember() {
        // 3-member party so it survives a leave.
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);
        partyService.invite(ALICE, CAROL, true);
        partyService.accept(CAROL);
        partyService.follow(BOB, ALICE, true);
        partyService.follow(CAROL, ALICE, true);

        // ALICE (the followed leader) leaves — both follow relationships pointing at her clear.
        partyService.leave(ALICE);

        assertTrue(partyService.followersOf(ALICE).isEmpty());
        assertTrue(partyService.leaderOf(BOB).isEmpty());
        assertTrue(partyService.leaderOf(CAROL).isEmpty());
    }

    @Test
    void leave_clearsFollowWhenFollowerLeaves() {
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);
        partyService.invite(ALICE, CAROL, true);
        partyService.accept(CAROL);
        partyService.follow(BOB, ALICE, true);

        partyService.leave(BOB);

        assertTrue(partyService.leaderOf(BOB).isEmpty());
        assertTrue(partyService.followersOf(ALICE).isEmpty());
    }

    @Test
    void disband_clearsAllFollowRelationships() {
        formPartyOf(ALICE, BOB);
        partyService.follow(BOB, ALICE, true);

        partyService.disband(ALICE);

        assertTrue(partyService.leaderOf(BOB).isEmpty());
        assertTrue(partyService.followersOf(ALICE).isEmpty());
    }

    @Test
    void clearFollowsInvolving_removesAsFollowerAndLeader() {
        partyService.form(ALICE);
        partyService.invite(ALICE, BOB, true);
        partyService.accept(BOB);
        partyService.invite(ALICE, CAROL, true);
        partyService.accept(CAROL);
        partyService.follow(BOB, ALICE, true);   // BOB follows ALICE
        partyService.follow(ALICE, CAROL, true); // ALICE follows CAROL

        // Simulate ALICE disconnecting: she is removed as both a follower and a leader.
        partyService.clearFollowsInvolving(ALICE);

        // ALICE is gone from both roles, and BOB (who followed ALICE) is now unshackled.
        assertTrue(partyService.leaderOf(ALICE).isEmpty());
        assertTrue(partyService.followersOf(ALICE).isEmpty());
        assertTrue(partyService.leaderOf(BOB).isEmpty());
        // CAROL had no relationships of her own beyond being ALICE's leader, now cleared.
        assertTrue(partyService.followersOf(CAROL).isEmpty());
    }
}
