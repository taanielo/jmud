package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Unit tests for {@link WorldEventScheduler}: the event fires within its configured tick bounds under
 * a seeded RNG, only one event runs at a time, the spawn and timeout broadcasts carry the right
 * content, and a killed event closes without a timeout broadcast.
 */
class WorldEventSchedulerTest {

    private static final RoomId ROOM_ID = RoomId.of("frozen-peaks-glacier");
    private static final AttackId ATTACK = AttackId.of("attack.rimewrought-stalker");

    private static MobTemplate eventTemplate() {
        return new MobTemplate(
            MobId.of("rimewrought-stalker"),
            "the Rimewrought Stalker",
            500,
            ATTACK,
            null,
            true,
            List.of(),
            ROOM_ID,
            1,
            0,
            780,
            null,
            List.of("world-event"),
            false,
            null,
            null,
            false,
            null,
            null,
            true,   // worldBoss
            true    // worldEvent
        );
    }

    @Test
    void firesWithinConfiguredIntervalBounds() {
        FakeStage stage = new FakeStage(eventTemplate());
        // interval offset 4 (initial rollInterval) then template index 0 on spawn.
        FakeRandom random = new FakeRandom();
        random.queue(4, 0);
        WorldEventScheduler scheduler = new WorldEventScheduler(
            stage, announcer(new CapturingBroadcaster()), random, 5, 10, 100);

        int ticks = 0;
        while (!scheduler.isEventActive() && ticks < 50) {
            scheduler.tick();
            ticks++;
        }

        assertTrue(scheduler.isEventActive(), "event should have opened");
        assertEquals(9, ticks, "event should fire after min(5) + rolled offset(4) ticks");
        assertTrue(ticks >= 5 && ticks <= 10, "fire time must lie within [5, 10], was " + ticks);
        assertEquals(1, stage.spawnCount, "exactly one mob should have spawned");
    }

    @Test
    void doesNotStartSecondEventWhileOneIsActive() {
        FakeStage stage = new FakeStage(eventTemplate());
        FakeRandom random = new FakeRandom();
        random.queue(0, 0); // interval == min, spawn index 0
        WorldEventScheduler scheduler = new WorldEventScheduler(
            stage, announcer(new CapturingBroadcaster()), random, 1, 1, 100);

        scheduler.tick(); // opens the event
        assertTrue(scheduler.isEventActive());

        for (int i = 0; i < 20; i++) {
            scheduler.tick();
        }

        assertTrue(scheduler.isEventActive(), "the single event should still be open");
        assertEquals(1, stage.spawnCount, "no second event may open while one is active");
    }

    @Test
    void announcesSpawnServerWide() {
        FakeStage stage = new FakeStage(eventTemplate());
        FakeRandom random = new FakeRandom();
        random.queue(0, 0);
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        WorldEventScheduler scheduler = new WorldEventScheduler(
            stage, announcer(broadcaster), random, 1, 1, 100);

        scheduler.tick();

        assertEquals(1, broadcaster.globals.size(), "spawn should broadcast exactly once");
        String text = broadcaster.text(0);
        assertTrue(text.contains("tears open"), "spawn text should describe the rift, got: " + text);
        assertTrue(text.contains("the Rimewrought Stalker"), "spawn text should name the mob, got: " + text);
        assertTrue(text.contains("Frozen Peaks Glacier"), "spawn text should name the room, got: " + text);
    }

    @Test
    void unkilledEventDespawnsWithTimeoutBroadcast() {
        FakeStage stage = new FakeStage(eventTemplate());
        FakeRandom random = new FakeRandom();
        random.queue(0, 0, 0); // interval, spawn index, next interval after close
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        WorldEventScheduler scheduler = new WorldEventScheduler(
            stage, announcer(broadcaster), random, 1, 1, 3);

        scheduler.tick(); // opens the event (window = 3)
        broadcaster.globals.clear();

        // The mob is never killed; three further ticks exhaust the window.
        scheduler.tick();
        scheduler.tick();
        scheduler.tick();

        assertFalse(scheduler.isEventActive(), "the event should have closed on timeout");
        assertEquals(1, stage.purged.size(), "the unkilled mob should be purged exactly once");
        assertEquals(1, broadcaster.globals.size(), "timeout should broadcast exactly once");
        String text = broadcaster.text(0);
        assertTrue(text.contains("collapses"), "timeout text should describe the collapse, got: " + text);
        assertTrue(text.contains("fades away"), "timeout text should say the mob fades, got: " + text);
    }

    @Test
    void killedEventClosesWithoutTimeoutBroadcast() {
        FakeStage stage = new FakeStage(eventTemplate());
        FakeRandom random = new FakeRandom();
        random.queue(0, 0, 0);
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        WorldEventScheduler scheduler = new WorldEventScheduler(
            stage, announcer(broadcaster), random, 1, 1, 100);

        scheduler.tick(); // opens the event
        broadcaster.globals.clear();

        // Simulate a player landing the killing blow before the window elapses.
        stage.lastSpawned.takeDamage(Integer.MAX_VALUE);
        scheduler.tick();

        assertFalse(scheduler.isEventActive(), "a slain event should close");
        assertEquals(1, stage.purged.size(), "the slain mob should be purged");
        assertTrue(broadcaster.globals.isEmpty(),
            "a kill must not emit a timeout broadcast (the kill path announces the death)");
    }

    // ── helpers ───────────────────────────────────────────────────────

    private WorldBossAnnouncer announcer(MessageBroadcaster broadcaster) {
        return new WorldBossAnnouncer(broadcaster, new RoomService(new StubRoomRepository(), ROOM_ID), null, null);
    }

    /** In-memory {@link WorldEventStage} that records spawns and purges. */
    private static final class FakeStage implements WorldEventStage {
        private final List<MobTemplate> templates;
        private final List<MobInstance> purged = new ArrayList<>();
        private int spawnCount;
        private MobInstance lastSpawned;

        private FakeStage(MobTemplate template) {
            this.templates = List.of(template);
        }

        @Override
        public List<MobTemplate> worldEventTemplates() {
            return templates;
        }

        @Override
        public Optional<MobInstance> spawnInstance(MobId mobId, RoomId roomId) {
            MobTemplate template = templates.stream()
                .filter(t -> t.id().equals(mobId))
                .findFirst()
                .orElse(null);
            if (template == null) {
                return Optional.empty();
            }
            MobInstance mob = new MobInstance(template);
            mob.moveTo(roomId);
            spawnCount++;
            lastSpawned = mob;
            return Optional.of(mob);
        }

        @Override
        public void purgeInstance(MobInstance mob) {
            purged.add(mob);
        }
    }

    /** Deterministic {@link CombatRandom} returning queued values (clamped), defaulting to the min. */
    private static final class FakeRandom implements CombatRandom {
        private final Deque<Integer> rolls = new ArrayDeque<>();

        void queue(int... values) {
            for (int v : values) {
                rolls.add(v);
            }
        }

        @Override
        public int roll(int minInclusive, int maxInclusive) {
            Integer v = rolls.poll();
            if (v == null) {
                return minInclusive;
            }
            return Math.max(minInclusive, Math.min(maxInclusive, v));
        }
    }

    private static final class CapturingBroadcaster implements MessageBroadcaster {
        private final List<Message> globals = new ArrayList<>();

        @Override
        public void sendToPlayer(Username target, Message message) {
        }

        @Override
        public void broadcastToRoom(RoomId room, Message message, Set<Username> exclude) {
        }

        @Override
        public void broadcastGlobal(Message message, Set<Username> exclude) {
            globals.add(message);
        }

        String text(int index) {
            return ((PlainTextMessage) globals.get(index)).text();
        }
    }

    private static final class StubRoomRepository implements RoomRepository {
        private final Room room =
            new Room(ROOM_ID, "Frozen Peaks Glacier", "A cracked blue glacier.", Map.of(), List.of(), List.of());

        @Override
        public void save(Room room) throws RepositoryException {
        }

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return room.getId().equals(id) ? Optional.of(room) : Optional.empty();
        }
    }
}
