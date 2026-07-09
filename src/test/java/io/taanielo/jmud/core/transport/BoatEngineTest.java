package io.taanielo.jmud.core.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.world.PlayerLocationService;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.repository.RoomRepository;

class BoatEngineTest {

    private static final RoomId DECK = RoomId.of("coastal-ferry-deck");
    private static final RoomId DOCK_A = RoomId.of("north-dock");
    private static final RoomId DOCK_B = RoomId.of("south-dock");
    private static final Username ALICE = Username.of("alice");

    private static Ferry ferry(int ticksPerLeg) {
        return new Ferry(
            FerryId.of("coastal-ferry"), "Coastal Ferry", DECK,
            List.of(DOCK_A, DOCK_B), ticksPerLeg, 0, List.of(), List.of());
    }

    @Test
    void carriesDeckPassengersToTheNextDockWhenTheCountdownElapses() {
        PlayerLocationService locations = new PlayerLocationService(new StubRoomRepository(), RoomId.of("start"));
        locations.movePlayerTo(ALICE, DECK);
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        BoatEngine engine = new BoatEngine(new LowerBoundRandom(), locations, broadcaster, List.of(ferry(2)));

        // Tick 1: countdown 2 -> 1, no departure yet.
        engine.tick();
        assertEquals(Optional.of(DECK), locations.findPlayerLocation(ALICE));
        assertEquals(DOCK_A, engine.currentDock(FerryId.of("coastal-ferry")));

        // Tick 2: countdown 1 -> 0, ferry departs and carries Alice to the next dock.
        engine.tick();
        assertEquals(Optional.of(DOCK_B), locations.findPlayerLocation(ALICE));
        assertEquals(DOCK_B, engine.currentDock(FerryId.of("coastal-ferry")));
    }

    @Test
    void notifiesDeckOriginAndDestinationOnDeparture() {
        PlayerLocationService locations = new PlayerLocationService(new StubRoomRepository(), RoomId.of("start"));
        locations.movePlayerTo(ALICE, DECK);
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        BoatEngine engine = new BoatEngine(new LowerBoundRandom(), locations, broadcaster, List.of(ferry(1)));

        engine.tick();

        List<RoomId> notified = broadcaster.roomMessages.stream().map(RecordedRoomMessage::room).toList();
        assertTrue(notified.contains(DECK), "deck passengers should be told the ferry is departing");
        assertTrue(notified.contains(DOCK_A), "the dock being left should hear the departure");
        assertTrue(notified.contains(DOCK_B), "the destination dock should hear the arrival");
    }

    @Test
    void cyclesBackToTheStartAfterTheLastDock() {
        PlayerLocationService locations = new PlayerLocationService(new StubRoomRepository(), RoomId.of("start"));
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        BoatEngine engine = new BoatEngine(new LowerBoundRandom(), locations, broadcaster, List.of(ferry(1)));
        FerryId id = FerryId.of("coastal-ferry");

        engine.tick(); // depart A -> B
        assertEquals(DOCK_B, engine.currentDock(id));
        engine.tick(); // depart B -> A (wrap around)
        assertEquals(DOCK_A, engine.currentDock(id));
    }

    @Test
    void producesTheSameArrivalSequenceGivenTheSameStartingState() {
        assertEquals(runArrivals(), runArrivals(),
            "a fixed ferry schedule must always produce the same sequence of arrivals");
    }

    private static List<RoomId> runArrivals() {
        PlayerLocationService locations = new PlayerLocationService(new StubRoomRepository(), RoomId.of("start"));
        RecordingBroadcaster broadcaster = new RecordingBroadcaster();
        BoatEngine engine = new BoatEngine(new LowerBoundRandom(), locations, broadcaster, List.of(ferry(3)));
        FerryId id = FerryId.of("coastal-ferry");
        List<RoomId> arrivals = new ArrayList<>();
        RoomId last = engine.currentDock(id);
        for (int i = 0; i < 12; i++) {
            engine.tick();
            RoomId now = engine.currentDock(id);
            if (!now.equals(last)) {
                arrivals.add(now);
                last = now;
            }
        }
        return arrivals;
    }

    @Test
    void runsMultipleFerriesOnIndependentSchedules() {
        Ferry slow = new Ferry(FerryId.of("slow"), "Slow", RoomId.of("slow-deck"),
            List.of(RoomId.of("s1"), RoomId.of("s2")), 3, 0, List.of(), List.of());
        Ferry fast = new Ferry(FerryId.of("fast"), "Fast", RoomId.of("fast-deck"),
            List.of(RoomId.of("f1"), RoomId.of("f2")), 1, 0, List.of(), List.of());
        PlayerLocationService locations = new PlayerLocationService(new StubRoomRepository(), RoomId.of("start"));
        BoatEngine engine = new BoatEngine(
            new LowerBoundRandom(), locations, new RecordingBroadcaster(), List.of(slow, fast));

        engine.tick();
        // Fast ferry (1 tick/leg) has already moved; slow ferry (3 ticks/leg) has not.
        assertEquals(RoomId.of("f2"), engine.currentDock(FerryId.of("fast")));
        assertEquals(RoomId.of("s1"), engine.currentDock(FerryId.of("slow")));
    }

    @Test
    void returnsNullDockForUnknownFerry() {
        PlayerLocationService locations = new PlayerLocationService(new StubRoomRepository(), RoomId.of("start"));
        BoatEngine engine = new BoatEngine(
            new LowerBoundRandom(), locations, new RecordingBroadcaster(), List.of(ferry(1)));
        assertNull(engine.currentDock(FerryId.of("ghost-ship")));
    }

    /** RNG stub that always returns the lower bound, making any flavour-line choice fixed. */
    private static final class LowerBoundRandom implements CombatRandom {
        @Override
        public int roll(int minInclusive, int maxInclusive) {
            return minInclusive;
        }
    }

    private static final class StubRoomRepository implements RoomRepository {
        @Override
        public void save(Room room) {
        }

        @Override
        public Optional<Room> findById(RoomId id) {
            return Optional.empty();
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
