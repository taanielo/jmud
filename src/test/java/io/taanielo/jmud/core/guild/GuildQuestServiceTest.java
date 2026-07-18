package io.taanielo.jmud.core.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link GuildQuestService}: lazy assignment, cooperative kill crediting, treasury
 * payout on completion, guild-wide announcement, objective re-roll, level banding and daily rotation.
 */
class GuildQuestServiceTest {

    private static final Username ALICE = Username.of("Alice");
    private static final Username BOB = Username.of("Bob");

    private FakeGuildRepository repository;
    private GuildService guildService;
    private RecordingBroadcaster broadcaster;
    private GuildQuestService service;

    @BeforeEach
    void setUp() throws Exception {
        repository = new FakeGuildRepository();
        guildService = new GuildService(repository);
        broadcaster = new RecordingBroadcaster();
        service = new GuildQuestService(guildService, pool(), broadcaster);
    }

    private static GuildQuestPool pool() {
        return new GuildQuestPool(List.of(
            new GuildQuestObjective("a", "Rat Purge", "rat", "rats", 2, 100, 1),
            new GuildQuestObjective("b", "Goblin Break", "goblin", "goblins", 3, 150, 1),
            new GuildQuestObjective("c", "Dragon Slaying", "dragon", "dragons", 1, 999, 3)));
    }

    @Test
    void activeQuestForGuildlessPlayerIsEmpty() {
        assertTrue(service.activeQuestFor(ALICE).isEmpty());
    }

    @Test
    void lazilyAssignsLevelOneObjectiveOnFirstAccess() {
        guildService.create(ALICE, "Ironclad");

        GuildQuest quest = service.activeQuestFor(ALICE).orElseThrow();

        // rotationCounter 0, level-1 guild -> first level-1 objective.
        assertEquals("a", quest.questId());
        assertEquals(0, quest.currentKills());
        assertEquals(2, quest.requiredKills());
        // Assignment is persisted on the guild.
        assertTrue(guildService.guildOf(ALICE).orElseThrow().activeGuildQuest() != null);
    }

    @Test
    void memberKillCreditsSharedGuildProgress() {
        guildService.create(ALICE, "Ironclad");
        service.activeQuestFor(ALICE); // assign "a" (rat)

        service.recordKill(ALICE, "rat");

        GuildQuest quest = service.activeQuestFor(ALICE).orElseThrow();
        assertEquals(1, quest.currentKills());
        assertFalse(quest.isComplete());
    }

    @Test
    void killByAnyMemberProgressesTheWholeGuild() {
        guildService.create(ALICE, "Ironclad");
        guildService.invite(ALICE, BOB, true);
        guildService.accept(BOB);
        service.activeQuestFor(ALICE); // assign "a" (rat, required 2)

        service.recordKill(ALICE, "rat");
        service.recordKill(BOB, "rat");

        // Bob's kill completed the shared objective (required 2) and rolled a fresh one (progress 0).
        // Progress is visible to every member, not just the leader.
        assertEquals(0, service.activeQuestFor(BOB).orElseThrow().currentKills());
        assertEquals(100, guildService.guildOf(ALICE).orElseThrow().treasuryGold());
    }

    @Test
    void nonMatchingKillDoesNotProgress() {
        guildService.create(ALICE, "Ironclad");
        service.activeQuestFor(ALICE); // "a" targets rat

        service.recordKill(ALICE, "goblin");

        assertEquals(0, service.activeQuestFor(ALICE).orElseThrow().currentKills());
    }

    @Test
    void guildlessKillIsNoOp() {
        // No guild for Alice; must not throw and must not create state.
        service.recordKill(ALICE, "rat");
        assertTrue(service.activeQuestFor(ALICE).isEmpty());
    }

    @Test
    void completionPaysTreasuryCountsToLifetimeAnnouncesAndRerolls() {
        guildService.create(ALICE, "Ironclad");
        service.activeQuestFor(ALICE); // "a" (rat, required 2, reward 100)
        broadcaster.messages.clear();

        service.recordKill(ALICE, "rat");
        service.recordKill(ALICE, "rat"); // completes

        Guild guild = guildService.guildOf(ALICE).orElseThrow();
        assertEquals(100, guild.treasuryGold());
        assertEquals(100, guild.lifetimeDepositedGold());
        // A fresh objective is active again with zero progress (no double credit).
        GuildQuest rerolled = guild.activeGuildQuest();
        assertEquals(0, rerolled.currentKills());
        // Announced on the [Guild] channel: completion + new objective, to Alice.
        List<String> aliceMsgs = broadcaster.textsFor(ALICE);
        assertTrue(aliceMsgs.stream().anyMatch(m -> m.contains("Guild quest complete")));
        assertTrue(aliceMsgs.stream().anyMatch(m -> m.contains("new guild quest has been posted")));
    }

    @Test
    void completedQuestIsNotDoubleCreditedByFurtherKills() {
        guildService.create(ALICE, "Ironclad");
        service.activeQuestFor(ALICE); // "a" (rat, required 2, reward 100)

        service.recordKill(ALICE, "rat");
        service.recordKill(ALICE, "rat"); // completes -> treasury 100, fresh "a"
        service.recordKill(ALICE, "rat"); // progresses the fresh objective only

        Guild guild = guildService.guildOf(ALICE).orElseThrow();
        assertEquals(100, guild.treasuryGold(), "no second payout");
        assertEquals(1, guild.activeGuildQuest().currentKills());
    }

    @Test
    void levelOneGuildIsNeverAssignedAHighBandObjective() {
        guildService.create(ALICE, "Ironclad");

        // Rotate through several days; a level-1 guild must only ever see level-1 objectives.
        for (int i = 0; i < 6; i++) {
            GuildQuest quest = service.activeQuestFor(ALICE).orElseThrow();
            assertTrue(quest.questId().equals("a") || quest.questId().equals("b"),
                "level-1 guild got a high-band objective: " + quest.questId());
            service.rotate();
        }
    }

    @Test
    void rotateReassignsEveryGuildAndResetsProgress() {
        guildService.create(ALICE, "Ironclad");
        guildService.create(BOB, "Redhand");
        service.activeQuestFor(ALICE); // "a"
        service.recordKill(ALICE, "rat"); // progress 1

        service.rotate(); // counter -> 1, level-1 candidates index 1 -> "b"

        assertEquals("b", service.activeQuestFor(ALICE).orElseThrow().questId());
        assertEquals(0, service.activeQuestFor(ALICE).orElseThrow().currentKills());
        assertEquals("b", service.activeQuestFor(BOB).orElseThrow().questId());
    }

    /** Records every delivered message per recipient so assertions can inspect the [Guild] channel. */
    private static final class RecordingBroadcaster implements MessageBroadcaster {
        private final List<Map.Entry<Username, String>> messages = new ArrayList<>();

        @Override
        public void sendToPlayer(Username target, Message message) {
            messages.add(Map.entry(target, ((PlainTextMessage) message).text()));
        }

        @Override
        public void broadcastToRoom(RoomId room, Message message, Set<Username> exclude) {
        }

        @Override
        public void broadcastGlobal(Message message, Set<Username> exclude) {
        }

        List<String> textsFor(Username user) {
            return messages.stream().filter(e -> e.getKey().equals(user)).map(Map.Entry::getValue).toList();
        }
    }

    /** In-memory {@link GuildRepository} mirroring the write-behind repository synchronously. */
    private static final class FakeGuildRepository implements GuildRepository {
        private final Map<GuildId, Guild> saved = new ConcurrentHashMap<>();

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
        }
    }
}
