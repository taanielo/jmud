package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link ArenaEventTicker} covering interval-driven firing, the two-player minimum,
 * concurrent-pool limiting, and spectator start/end notifications.
 */
class ArenaEventTickerTest {

    /** Records every delivery so tests can assert on global and room-scoped announcements. */
    private static final class RecordingBroadcaster implements MessageBroadcaster {
        private final List<Message> global = new ArrayList<>();
        private final List<Message> direct = new ArrayList<>();
        private final Map<RoomId, List<Message>> room = new HashMap<>();

        @Override
        public void sendToPlayer(Username target, Message message) {
            direct.add(message);
        }

        @Override
        public void broadcastToRoom(RoomId roomId, Message message, Set<Username> exclude) {
            room.computeIfAbsent(roomId, ignored -> new ArrayList<>()).add(message);
        }

        @Override
        public void broadcastGlobal(Message message, Set<Username> exclude) {
            global.add(message);
        }
    }

    /** Deterministic RNG that replays scripted rolls, falling back to the lower bound. */
    private static final class ScriptedRandom implements CombatRandom {
        private final Deque<Integer> rolls = new ArrayDeque<>();

        void push(int... values) {
            for (int value : values) {
                rolls.addLast(value);
            }
        }

        @Override
        public int roll(int minInclusive, int maxInclusive) {
            Integer next = rolls.pollFirst();
            return next == null ? minInclusive : next;
        }
    }

    private static final Username ALICE = Username.of("Alice");
    private static final Username BOB = Username.of("Bob");

    private DuelService duelService;
    private RecordingBroadcaster broadcaster;
    private ScriptedRandom random;
    private List<Username> online;

    @BeforeEach
    void setUp() {
        duelService = new DuelService();
        broadcaster = new RecordingBroadcaster();
        random = new ScriptedRandom();
        online = new ArrayList<>(List.of(ALICE, BOB));
    }

    private ArenaEventTicker ticker(int interval, int poolSize) {
        return new ArenaEventTicker(duelService, broadcaster, () -> List.copyOf(online), random, interval, poolSize);
    }

    private void tickTimes(ArenaEventTicker ticker, int times) {
        for (int i = 0; i < times; i++) {
            ticker.tick();
        }
    }

    @Test
    void doesNotFireBeforeIntervalElapses() {
        ArenaEventTicker ticker = ticker(5, 1);
        tickTimes(ticker, 4);
        assertTrue(broadcaster.global.isEmpty());
        assertTrue(duelService.pendingChallenger(BOB).isEmpty());
    }

    @Test
    void firesDuelChallengeAtInterval() {
        // roll(0, size-1) -> 0 = Alice; roll(0, size-2) -> 0, bumped to 1 = Bob.
        random.push(0, 0);
        ArenaEventTicker ticker = ticker(5, 1);
        tickTimes(ticker, 5);

        assertEquals(1, broadcaster.global.size());
        assertEquals(ALICE, duelService.pendingChallenger(BOB).orElseThrow());
        // Both combatants are individually prompted, and the arena rooms are told a duel is announced.
        assertEquals(2, broadcaster.direct.size());
        assertFalse(broadcaster.room.getOrDefault(ArenaEventTicker.ARENA_PIT, List.of()).isEmpty());
    }

    @Test
    void skipsEventWhenFewerThanTwoPlayersOnline() {
        online = new ArrayList<>(List.of(ALICE));
        ArenaEventTicker ticker = ticker(3, 1);
        tickTimes(ticker, 6);
        assertTrue(broadcaster.global.isEmpty());
        assertTrue(duelService.pendingChallenger(ALICE).isEmpty());
    }

    @Test
    void doesNotExceedConcurrentPoolSize() {
        random.push(0, 0, 0, 0);
        ArenaEventTicker ticker = ticker(2, 1);
        tickTimes(ticker, 2); // fire first challenge (pending, occupies the pool)
        assertEquals(1, ticker.trackedDuelCount());
        tickTimes(ticker, 2); // pool full and challenge still pending -> no second event
        assertEquals(1, broadcaster.global.size());
        assertEquals(1, ticker.trackedDuelCount());
    }

    @Test
    void notifiesSpectatorsWhenDuelStartsAndEnds() {
        random.push(0, 0);
        // A long interval ensures the manual accept/end ticks below never trigger a second event.
        ArenaEventTicker ticker = ticker(50, 1);
        tickTimes(ticker, 50); // announce challenge
        int afterAnnounce = broadcaster.room.get(ArenaEventTicker.ARENA_PIT).size();

        // The target accepts: the duel becomes active.
        duelService.activate(ALICE, BOB);
        ticker.tick();
        int afterStart = broadcaster.room.get(ArenaEventTicker.ARENA_PIT).size();
        assertTrue(afterStart > afterAnnounce, "spectators should hear the duel begin");

        // The duel resolves.
        duelService.endDuel(ALICE, BOB);
        ticker.tick();
        int afterEnd = broadcaster.room.get(ArenaEventTicker.ARENA_PIT).size();
        assertTrue(afterEnd > afterStart, "spectators should hear the duel end");
        assertEquals(0, ticker.trackedDuelCount());
    }
}
