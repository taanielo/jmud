package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.world.repository.RoomRepository;

class AmbientMessageEngineTest {

    private static final RoomId CATACOMBS = RoomId.of("catacombs");
    private static final RoomId SILENT = RoomId.of("silent-hall");
    private static final RoomId EMPTY_ROOM = RoomId.of("empty-crypt");

    private static final List<String> CATACOMB_MESSAGES = List.of(
        "Water drips somewhere in the dark.",
        "A cold draft sighs through the passage.");

    @Test
    void emitsFirstMessageAfterTheRolledIntervalElapses() {
        StubRoomRepository rooms = new StubRoomRepository();
        rooms.add(ambientRoom(CATACOMBS, CATACOMB_MESSAGES));
        StubPlayerLocations locations = new StubPlayerLocations(Set.of(CATACOMBS));
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        // roll always returns the lower bound: interval = 3, message index = 0.
        AmbientMessageEngine engine = new AmbientMessageEngine(
            new LowerBoundRandom(), rooms, locations, broadcaster, 3, 3);

        // Tick 1 seeds the countdown (3); ticks 2-4 decrement it. Emission fires when it hits 0.
        engine.tick();
        engine.tick();
        engine.tick();
        assertTrue(broadcaster.roomMessages.isEmpty(), "no emission before the interval elapses");

        engine.tick();

        assertEquals(1, broadcaster.roomMessages.size());
        RecordedRoomMessage sent = broadcaster.roomMessages.get(0);
        assertEquals(CATACOMBS, sent.room());
        assertEquals(CATACOMB_MESSAGES.get(0), sent.text());
    }

    @Test
    void neverEmitsToRoomsWithoutAmbientMessages() {
        StubRoomRepository rooms = new StubRoomRepository();
        rooms.add(new Room(SILENT, "Silent Hall", "Utterly quiet.", Map.of(), List.of(), List.of()));
        StubPlayerLocations locations = new StubPlayerLocations(Set.of(SILENT));
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        AmbientMessageEngine engine = new AmbientMessageEngine(
            new LowerBoundRandom(), rooms, locations, broadcaster, 2, 2);

        for (int i = 0; i < 20; i++) {
            engine.tick();
        }

        assertTrue(broadcaster.roomMessages.isEmpty(), "silent rooms must stay silent");
    }

    @Test
    void neverEmitsToUnoccupiedAmbientRooms() {
        StubRoomRepository rooms = new StubRoomRepository();
        rooms.add(ambientRoom(EMPTY_ROOM, CATACOMB_MESSAGES));
        StubPlayerLocations locations = new StubPlayerLocations(Set.of());
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        AmbientMessageEngine engine = new AmbientMessageEngine(
            new LowerBoundRandom(), rooms, locations, broadcaster, 2, 2);

        for (int i = 0; i < 20; i++) {
            engine.tick();
        }

        assertTrue(broadcaster.roomMessages.isEmpty(), "empty rooms must not emit");
    }

    @Test
    void repeatsEmissionsOnEachInterval() {
        StubRoomRepository rooms = new StubRoomRepository();
        rooms.add(ambientRoom(CATACOMBS, CATACOMB_MESSAGES));
        StubPlayerLocations locations = new StubPlayerLocations(Set.of(CATACOMBS));
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        AmbientMessageEngine engine = new AmbientMessageEngine(
            new LowerBoundRandom(), rooms, locations, broadcaster, 3, 3);

        // Interval 3: emissions land on tick 4, tick 7, tick 10 (seed + 3-tick countdown each cycle).
        for (int i = 0; i < 10; i++) {
            engine.tick();
        }

        assertEquals(3, broadcaster.roomMessages.size());
    }

    @Test
    void rejectsNonPositiveMinimumInterval() {
        assertThrows(IllegalArgumentException.class, () -> new AmbientMessageEngine(
            new LowerBoundRandom(), new StubRoomRepository(),
            new StubPlayerLocations(Set.of()), new RecordingBroadcaster(), 0, 5));
    }

    @Test
    void rejectsMaximumBelowMinimum() {
        assertThrows(IllegalArgumentException.class, () -> new AmbientMessageEngine(
            new LowerBoundRandom(), new StubRoomRepository(),
            new StubPlayerLocations(Set.of()), new RecordingBroadcaster(), 10, 5));
    }

    private static Room ambientRoom(RoomId id, List<String> messages) {
        return new Room(id, "Ambient Room", "A room with atmosphere.", Map.of(), List.of(), List.of(),
            Map.of(), null, null, null, false, messages);
    }

    /** RNG stub that always returns the lower bound, making intervals and message choice fixed. */
    private static final class LowerBoundRandom implements CombatRandom {
        @Override
        public int roll(int minInclusive, int maxInclusive) {
            return minInclusive;
        }
    }

    private static final class StubRoomRepository implements RoomRepository {
        private final Map<RoomId, Room> rooms = new ConcurrentHashMap<>();

        void add(Room room) {
            rooms.put(room.getId(), room);
        }

        @Override
        public void save(Room room) {
            rooms.put(room.getId(), room);
        }

        @Override
        public Optional<Room> findById(RoomId id) {
            return Optional.ofNullable(rooms.get(id));
        }
    }

    /**
     * Minimal {@link PlayerLocationService} subclass that reports a fixed set of occupied rooms;
     * the engine only calls {@link PlayerLocationService#occupiedRooms()}.
     */
    private static final class StubPlayerLocations extends PlayerLocationService {
        private final Set<RoomId> occupied;

        StubPlayerLocations(Set<RoomId> occupied) {
            super(new StubRoomRepository(), RoomId.of("start"));
            this.occupied = occupied;
        }

        @Override
        public Set<RoomId> occupiedRooms() {
            return occupied;
        }
    }

    private record RecordedRoomMessage(RoomId room, String text) {
    }

    private static final class RecordingBroadcaster implements MessageBroadcaster {
        private final List<RecordedRoomMessage> roomMessages = new ArrayList<>();

        @Override
        public void sendToPlayer(Username target, Message message) {
        }

        @Override
        public void broadcastToRoom(RoomId room, Message message, Set<Username> exclude) {
            if (!(message instanceof PlainTextMessage plain)) {
                throw new IllegalStateException("Expected a PlainTextMessage, got " + message.getClass());
            }
            roomMessages.add(new RecordedRoomMessage(room, plain.text()));
        }

        @Override
        public void broadcastGlobal(Message message, Set<Username> exclude) {
        }
    }
}
