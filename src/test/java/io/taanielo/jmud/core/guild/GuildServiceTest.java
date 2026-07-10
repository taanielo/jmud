package io.taanielo.jmud.core.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;

class GuildServiceTest {

    private static final Username ALICE = Username.of("Alice");
    private static final Username BOB = Username.of("Bob");
    private static final Username CAROL = Username.of("Carol");

    private FakeGuildRepository repository;
    private GuildService service;

    @BeforeEach
    void setUp() throws Exception {
        repository = new FakeGuildRepository();
        service = new GuildService(repository);
    }

    @Test
    void createFoundsGuildWithLeader() {
        GuildResult result = service.create(ALICE, "Ironclad");

        assertTrue(result.success());
        assertEquals("Ironclad", result.guild().name());
        assertTrue(result.guild().isLeader(ALICE));
        assertTrue(service.guildOf(ALICE).isPresent());
        assertEquals(1, repository.saved.size());
    }

    @Test
    void createRejectsDuplicateNameCaseInsensitively() {
        service.create(ALICE, "Ironclad");

        GuildResult result = service.create(BOB, "IRONCLAD");

        assertFalse(result.success());
        assertTrue(service.guildOf(BOB).isEmpty());
    }

    @Test
    void createRejectsTooShortName() {
        assertFalse(service.create(ALICE, "ab").success());
    }

    @Test
    void createRejectsPlayerAlreadyInGuild() {
        service.create(ALICE, "Ironclad");

        assertFalse(service.create(ALICE, "Redhand").success());
    }

    @Test
    void inviteAndAcceptAddsMember() {
        service.create(ALICE, "Ironclad");

        GuildResult invite = service.invite(ALICE, BOB, true);
        assertTrue(invite.success());

        GuildResult accept = service.accept(BOB);
        assertTrue(accept.success());
        assertTrue(service.guildOf(BOB).isPresent());
        assertEquals("Ironclad", service.guildTag(BOB).orElseThrow());
    }

    @Test
    void inviteRejectedForNonLeader() {
        service.create(ALICE, "Ironclad");
        service.invite(ALICE, BOB, true);
        service.accept(BOB);

        GuildResult invite = service.invite(BOB, CAROL, true);

        assertFalse(invite.success());
    }

    @Test
    void inviteRejectedWhenTargetOffline() {
        service.create(ALICE, "Ironclad");

        assertFalse(service.invite(ALICE, BOB, false).success());
    }

    @Test
    void cannotJoinSecondGuildWhileInOne() {
        service.create(ALICE, "Ironclad");
        service.invite(ALICE, BOB, true);
        service.accept(BOB);
        service.create(CAROL, "Redhand");

        GuildResult invite = service.invite(CAROL, BOB, true);

        assertFalse(invite.success());
    }

    @Test
    void declineDropsInvite() {
        service.create(ALICE, "Ironclad");
        service.invite(ALICE, BOB, true);

        assertTrue(service.decline(BOB).success());
        assertFalse(service.accept(BOB).success());
    }

    @Test
    void reissuingInviteReplacesPreviousGuild() {
        service.create(ALICE, "Ironclad");
        service.create(CAROL, "Redhand");
        service.invite(ALICE, BOB, true);
        service.invite(CAROL, BOB, true);

        service.accept(BOB);

        assertEquals("Redhand", service.guildTag(BOB).orElseThrow());
    }

    @Test
    void leaveTransfersLeadershipWhenLeaderLeaves() {
        service.create(ALICE, "Ironclad");
        service.invite(ALICE, BOB, true);
        service.accept(BOB);

        GuildResult result = service.leave(ALICE);

        assertTrue(result.success());
        assertEquals(BOB, service.guildOf(BOB).orElseThrow().leaderId());
        assertTrue(service.guildOf(ALICE).isEmpty());
    }

    @Test
    void leaveByLastMemberDisbands() {
        service.create(ALICE, "Ironclad");

        GuildResult result = service.leave(ALICE);

        assertTrue(result.success());
        assertTrue(service.guildOf(ALICE).isEmpty());
        assertTrue(repository.deleted.size() >= 1);
    }

    @Test
    void kickIsLeaderOnly() {
        service.create(ALICE, "Ironclad");
        service.invite(ALICE, BOB, true);
        service.accept(BOB);

        assertFalse(service.kick(BOB, ALICE).success());

        GuildResult kick = service.kick(ALICE, BOB);
        assertTrue(kick.success());
        assertTrue(service.guildOf(BOB).isEmpty());
    }

    @Test
    void leaderCanPromoteMemberToOfficer() {
        service.create(ALICE, "Ironclad");
        service.invite(ALICE, BOB, true);
        service.accept(BOB);

        GuildResult result = service.promote(ALICE, BOB);

        assertTrue(result.success());
        assertTrue(service.guildOf(BOB).orElseThrow().isOfficer(BOB));
        assertEquals(GuildRank.OFFICER,
            repository.saved.get(result.guild().id()).member(BOB).orElseThrow().rank());
    }

    @Test
    void promoteRejectsNonLeaderAndAlreadyOfficerAndSelfAndNonMember() {
        service.create(ALICE, "Ironclad");
        service.invite(ALICE, BOB, true);
        service.accept(BOB);
        service.invite(ALICE, CAROL, true);
        service.accept(CAROL);

        // An officer cannot promote another member.
        service.promote(ALICE, BOB);
        assertFalse(service.promote(BOB, CAROL).success());
        // Leader cannot promote self.
        assertFalse(service.promote(ALICE, ALICE).success());
        // Promoting an existing officer is a no-op-with-message.
        assertFalse(service.promote(ALICE, BOB).success());
        // Cannot promote a non-member.
        assertFalse(service.promote(ALICE, Username.of("Dave")).success());
    }

    @Test
    void leaderCanDemoteOfficerBackToMember() {
        service.create(ALICE, "Ironclad");
        service.invite(ALICE, BOB, true);
        service.accept(BOB);
        service.promote(ALICE, BOB);

        GuildResult result = service.demote(ALICE, BOB);

        assertTrue(result.success());
        assertFalse(service.guildOf(BOB).orElseThrow().isOfficer(BOB));
        assertEquals(GuildRank.MEMBER,
            service.guildOf(BOB).orElseThrow().member(BOB).orElseThrow().rank());
    }

    @Test
    void demoteIsNoOpWhenTargetNotOfficer() {
        service.create(ALICE, "Ironclad");
        service.invite(ALICE, BOB, true);
        service.accept(BOB);

        assertFalse(service.demote(ALICE, BOB).success());
    }

    @Test
    void demoteRejectsNonLeader() {
        service.create(ALICE, "Ironclad");
        service.invite(ALICE, BOB, true);
        service.accept(BOB);
        service.promote(ALICE, BOB);

        // Officer cannot demote themselves or anyone else.
        assertFalse(service.demote(BOB, BOB).success());
        assertTrue(service.guildOf(BOB).orElseThrow().isOfficer(BOB));
    }

    @Test
    void officerCanInviteAndKick() {
        service.create(ALICE, "Ironclad");
        service.invite(ALICE, BOB, true);
        service.accept(BOB);
        service.promote(ALICE, BOB);

        GuildResult invite = service.invite(BOB, CAROL, true);
        assertTrue(invite.success());
        service.accept(CAROL);

        GuildResult kick = service.kick(BOB, CAROL);
        assertTrue(kick.success());
        assertTrue(service.guildOf(CAROL).isEmpty());
    }

    @Test
    void officerCannotPromoteDemoteWithdrawOrDisband() {
        service.create(ALICE, "Ironclad");
        service.invite(ALICE, BOB, true);
        service.accept(BOB);
        service.invite(ALICE, CAROL, true);
        service.accept(CAROL);
        service.promote(ALICE, BOB);
        service.deposit(ALICE, 50);

        assertFalse(service.promote(BOB, CAROL).success());
        assertFalse(service.demote(BOB, BOB).success());
        assertFalse(service.withdraw(BOB, 10).success());
        assertFalse(service.disband(BOB).success());
        assertTrue(service.guildOf(ALICE).isPresent());
    }

    @Test
    void plainMemberCanModerateNothing() {
        service.create(ALICE, "Ironclad");
        service.invite(ALICE, BOB, true);
        service.accept(BOB);
        service.invite(ALICE, CAROL, true);
        service.accept(CAROL);

        assertFalse(service.invite(BOB, Username.of("Dave"), true).success());
        assertFalse(service.kick(BOB, CAROL).success());
        assertFalse(service.promote(BOB, CAROL).success());
        assertFalse(service.demote(BOB, CAROL).success());
    }

    @Test
    void officerCannotKickLeader() {
        service.create(ALICE, "Ironclad");
        service.invite(ALICE, BOB, true);
        service.accept(BOB);
        service.promote(ALICE, BOB);

        assertFalse(service.kick(BOB, ALICE).success());
        assertTrue(service.guildOf(ALICE).isPresent());
    }

    @Test
    void disbandIsLeaderOnlyAndClearsEveryMember() {
        service.create(ALICE, "Ironclad");
        service.invite(ALICE, BOB, true);
        service.accept(BOB);

        assertFalse(service.disband(BOB).success());

        GuildResult disband = service.disband(ALICE);
        assertTrue(disband.success());
        assertEquals(2, disband.guild().memberCount());
        assertTrue(service.guildOf(ALICE).isEmpty());
        assertTrue(service.guildOf(BOB).isEmpty());
    }

    @Test
    void loadsPersistedGuildsAtStartup() throws Exception {
        service.create(ALICE, "Ironclad");
        service.invite(ALICE, BOB, true);
        service.accept(BOB);

        GuildService reloaded = new GuildService(repository);

        assertTrue(reloaded.guildOf(ALICE).isPresent());
        assertTrue(reloaded.guildOf(BOB).isPresent());
        assertEquals("Ironclad", reloaded.guildTag(BOB).orElseThrow());
    }

    @Test
    void depositIncreasesTreasuryAndPersists() {
        service.create(ALICE, "Ironclad");

        GuildResult result = service.deposit(ALICE, 50);

        assertTrue(result.success());
        assertEquals(50, result.guild().treasuryGold());
        assertEquals(50, service.guildOf(ALICE).orElseThrow().treasuryGold());
        assertEquals(50, repository.saved.get(result.guild().id()).treasuryGold());
    }

    @Test
    void memberCanDepositAndLeaderCanWithdraw() {
        service.create(ALICE, "Ironclad");
        service.invite(ALICE, BOB, true);
        service.accept(BOB);

        assertTrue(service.deposit(BOB, 80).success());

        GuildResult withdraw = service.withdraw(ALICE, 30);
        assertTrue(withdraw.success());
        assertEquals(50, withdraw.guild().treasuryGold());
    }

    @Test
    void nonLeaderCannotWithdraw() {
        service.create(ALICE, "Ironclad");
        service.invite(ALICE, BOB, true);
        service.accept(BOB);
        service.deposit(BOB, 80);

        GuildResult result = service.withdraw(BOB, 10);

        assertFalse(result.success());
        assertEquals(80, service.guildOf(ALICE).orElseThrow().treasuryGold());
    }

    @Test
    void withdrawMoreThanTreasuryFails() {
        service.create(ALICE, "Ironclad");
        service.deposit(ALICE, 40);

        GuildResult result = service.withdraw(ALICE, 41);

        assertFalse(result.success());
        assertEquals(40, service.guildOf(ALICE).orElseThrow().treasuryGold());
    }

    @Test
    void depositRejectsNonPositiveAmount() {
        service.create(ALICE, "Ironclad");

        assertFalse(service.deposit(ALICE, 0).success());
        assertFalse(service.deposit(ALICE, -5).success());
        assertEquals(0, service.guildOf(ALICE).orElseThrow().treasuryGold());
    }

    @Test
    void guildlessPlayerCannotDepositOrWithdraw() {
        assertFalse(service.deposit(ALICE, 10).success());
        assertFalse(service.withdraw(ALICE, 10).success());
    }

    /** In-memory {@link GuildRepository} that mirrors the write-behind repository synchronously. */
    private static final class FakeGuildRepository implements GuildRepository {
        private final Map<GuildId, Guild> saved = new ConcurrentHashMap<>();
        private final List<GuildId> deleted = new ArrayList<>();

        @Override
        public List<Guild> loadAll() {
            return List.copyOf(saved.values());
        }

        @Override
        public void save(Guild guild) {
            saved.put(guild.id(), guild);
        }

        @Override
        public void delete(GuildId guildId) {
            saved.remove(guildId);
            deleted.add(guildId);
        }
    }
}
