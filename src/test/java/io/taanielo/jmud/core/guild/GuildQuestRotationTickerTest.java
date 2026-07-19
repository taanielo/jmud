package io.taanielo.jmud.core.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.WorldClock;

/**
 * Unit tests for {@link GuildQuestRotationTicker}: a night-to-day transition rotates every guild's
 * cooperative guild quest.
 */
class GuildQuestRotationTickerTest {

    private static final Username ALICE = Username.of("Alice");

    private WorldClock worldClock;
    private GuildService guildService;
    private GuildQuestService guildQuestService;
    private GuildQuestRotationTicker ticker;

    @BeforeEach
    void setUp() throws Exception {
        worldClock = new WorldClock(1);
        guildService = new GuildService(new FakeGuildRepository());
        GuildQuestPool pool = new GuildQuestPool(List.of(
            new GuildQuestObjective("a", "A", "rat", "rats", 5, 100, 1),
            new GuildQuestObjective("b", "B", "goblin", "goblins", 5, 100, 1)));
        guildQuestService = new GuildQuestService(guildService, pool, new NoopBroadcaster());
        ticker = new GuildQuestRotationTicker(worldClock, guildQuestService);
        guildService.create(ALICE, "Ironclad");
    }

    private void advanceOneTick() {
        worldClock.tick();
        ticker.tick();
    }

    @Test
    void doesNotRotateOnDayToNightTransition() {
        guildQuestService.activeQuestFor(ALICE); // assign "a"
        advanceOneTick(); // DAY -> NIGHT, not a new day
        assertEquals(0, guildQuestService.rotationCounter());
        assertEquals("a", guildQuestService.activeQuestFor(ALICE).orElseThrow().questId());
    }

    @Test
    void rotatesOnNightToDayTransition() {
        guildQuestService.activeQuestFor(ALICE); // assign "a"
        advanceOneTick(); // DAY -> NIGHT
        advanceOneTick(); // NIGHT -> DAY, new day -> rotate

        assertEquals(1, guildQuestService.rotationCounter());
        assertEquals("b", guildQuestService.activeQuestFor(ALICE).orElseThrow().questId());
    }

    private static final class NoopBroadcaster implements MessageBroadcaster {
        @Override
        public void sendToPlayer(Username target, Message message) {
        }

        @Override
        public void broadcastToRoom(RoomId room, Message message, Set<Username> exclude) {
        }

        @Override
        public void broadcastGlobal(Message message, Set<Username> exclude) {
        }
    }

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
