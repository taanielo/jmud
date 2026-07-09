package io.taanielo.jmud.core.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

class WeatherEngineTest {

    private static final RoomId OUTDOOR = RoomId.of("field");
    private static final RoomId INDOOR = RoomId.of("hall");

    @Test
    void weatherStartsClear() {
        WeatherEngine engine = new WeatherEngine(new TargetRandom(50, 100), repo(), 5, 10);

        assertEquals(Weather.clear(), engine.current());
    }

    @Test
    void intensityChangesSmoothlyByAtMostOneStepPerTick() {
        WeatherEngine engine = new WeatherEngine(new TargetRandom(50, 100), repo(), 5, 10);

        int previous = engine.current().intensity();
        for (int i = 0; i < 60; i++) {
            engine.tick();
            int now = engine.current().intensity();
            assertTrue(Math.abs(now - previous) <= 10,
                "intensity jumped more than one step: " + previous + " -> " + now);
            previous = now;
        }
    }

    @Test
    void weatherBecomesActiveWhenTargetIsNonClear() {
        WeatherEngine engine = new WeatherEngine(new TargetRandom(50, 100), repo(), 5, 10);

        for (int i = 0; i < 60; i++) {
            engine.tick();
        }

        assertEquals(WeatherType.RAIN, engine.current().type());
        assertTrue(engine.current().isActive());
    }

    @Test
    void transitionsAreDeterministicForIdenticalRandomStreams() {
        WeatherEngine first = new WeatherEngine(new TargetRandom(75, 60), repo(), 4, 10);
        WeatherEngine second = new WeatherEngine(new TargetRandom(75, 60), repo(), 4, 10);

        for (int i = 0; i < 40; i++) {
            first.tick();
            second.tick();
            assertEquals(first.current(), second.current(), "weather diverged at tick " + i);
        }
    }

    @Test
    void weatherRampsBackToClearWhenTargetBecomesClear() {
        TargetRandom random = new TargetRandom(50, 100);
        WeatherEngine engine = new WeatherEngine(random, repo(), 5, 10);
        for (int i = 0; i < 60; i++) {
            engine.tick();
        }
        assertTrue(engine.current().isActive());

        random.typeRoll = 10; // now rolls CLEAR every transition
        for (int i = 0; i < 60; i++) {
            engine.tick();
        }

        assertEquals(Weather.clear(), engine.current());
    }

    @Test
    void weatherAppliesOnlyToOutdoorRooms() {
        WeatherEngine engine = new WeatherEngine(new TargetRandom(50, 100), repo(), 5, 10);
        for (int i = 0; i < 60; i++) {
            engine.tick();
        }
        assertTrue(engine.current().isActive());

        assertEquals(engine.current(), engine.getWeatherAt(OUTDOOR));
        assertEquals(Weather.clear(), engine.getWeatherAt(INDOOR));
    }

    @Test
    void getWeatherAtUnknownRoomIsClear() {
        WeatherEngine engine = new WeatherEngine(new TargetRandom(50, 100), repo(), 5, 10);
        for (int i = 0; i < 20; i++) {
            engine.tick();
        }

        assertEquals(Weather.clear(), engine.getWeatherAt(RoomId.of("nowhere")));
    }

    @Test
    void weatherStatePersistsAcrossTicks() {
        WeatherEngine engine = new WeatherEngine(new TargetRandom(50, 100), repo(), 5, 10);
        for (int i = 0; i < 30; i++) {
            engine.tick();
        }
        Weather snapshot = engine.current();

        // Reading twice without ticking must return the identical persisted snapshot.
        assertEquals(snapshot, engine.current());
    }

    private static RoomRepository repo() {
        Map<RoomId, Room> rooms = new HashMap<>();
        rooms.put(OUTDOOR, room(OUTDOOR, true));
        rooms.put(INDOOR, room(INDOOR, false));
        return new MapRoomRepository(rooms);
    }

    private static Room room(RoomId id, boolean outdoor) {
        return new Room(
            id, id.getValue(), "A place.", Map.of(), List.of(), List.of(),
            Map.of(), null, null, null, outdoor);
    }

    /** Deterministic random whose type/intensity rolls are fixed (and adjustable mid-run). */
    private static final class TargetRandom implements CombatRandom {
        private int typeRoll;
        private final int intensityRoll;

        private TargetRandom(int typeRoll, int intensityRoll) {
            this.typeRoll = typeRoll;
            this.intensityRoll = intensityRoll;
        }

        @Override
        public int roll(int minInclusive, int maxInclusive) {
            // The engine rolls (1,100) for the type and (30,100) for the target intensity.
            return minInclusive == 1 ? typeRoll : intensityRoll;
        }
    }

    private record MapRoomRepository(Map<RoomId, Room> rooms) implements RoomRepository {
        @Override
        public void save(Room room) {
            rooms.put(room.getId(), room);
        }

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            if (id == null) {
                throw new RepositoryException("Room id is required");
            }
            return Optional.ofNullable(rooms.get(id));
        }
    }
}
