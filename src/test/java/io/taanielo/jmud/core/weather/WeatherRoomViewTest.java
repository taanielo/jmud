package io.taanielo.jmud.core.weather;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

class WeatherRoomViewTest {

    @Test
    void outdoorRoomWithActiveWeatherGetsDescriptionAndVisibilityLines() {
        WeatherEngine engine = fogEngine();
        WeatherRoomView view = new WeatherRoomView(engine);

        List<String> lines = view.weatherLines(room("field", true));

        assertTrue(lines.stream().anyMatch(line -> line.toLowerCase(Locale.ROOT).contains("fog")),
            "expected a fog description line, got " + lines);
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("Visibility:")),
            "expected a visibility line, got " + lines);
    }

    @Test
    void indoorRoomGetsNoWeatherLines() {
        WeatherEngine engine = fogEngine();
        WeatherRoomView view = new WeatherRoomView(engine);

        assertTrue(view.weatherLines(room("hall", false)).isEmpty());
    }

    private static WeatherEngine fogEngine() {
        // Type roll 70 selects FOG (weights: <=40 clear, <=60 rain, <=80 fog); intensity ramps to 100.
        CombatRandom foggy = (min, max) -> min == 1 ? 70 : 100;
        WeatherEngine engine = new WeatherEngine(foggy, new SingleRoomRepository(), 5, 10);
        for (int i = 0; i < 60; i++) {
            engine.tick();
        }
        return engine;
    }

    private static Room room(String id, boolean outdoor) {
        return new Room(
            RoomId.of(id), id, "A place.", Map.of(), List.of(), List.of(),
            Map.of(), null, null, null, outdoor);
    }

    private static final class SingleRoomRepository implements RoomRepository {
        @Override
        public void save(Room room) {
        }

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return Optional.empty();
        }
    }
}
