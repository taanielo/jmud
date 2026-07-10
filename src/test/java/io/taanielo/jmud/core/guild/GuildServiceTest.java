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
