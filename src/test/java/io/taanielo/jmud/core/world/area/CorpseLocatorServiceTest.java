package io.taanielo.jmud.core.world.area;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.transport.Ferry;
import io.taanielo.jmud.core.transport.FerryId;
import io.taanielo.jmud.core.world.Corpse;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomPathfinder;

/**
 * Unit tests for {@link CorpseLocatorService}: the "no corpse / decayed", "standing on it", and
 * "elsewhere with ferry-aware directions" messaging, mirroring {@code WayfindServiceTest}'s style and
 * network-free approach.
 */
class CorpseLocatorServiceTest {

    private static final int DECAY_SECONDS = 300;
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final Map<RoomId, Map<Direction, RoomId>> exits = new HashMap<>();
    private final Map<RoomId, String> roomNames = new HashMap<>();

    private void corridor(String from, Direction direction, String to) {
        exits.computeIfAbsent(RoomId.of(from), id -> new LinkedHashMap<>()).put(direction, RoomId.of(to));
        exits.computeIfAbsent(RoomId.of(to), id -> new LinkedHashMap<>())
            .put(direction.opposite(), RoomId.of(from));
    }

    private CorpseLocatorService service(List<Ferry> ferries) {
        Function<RoomId, Map<Direction, RoomId>> provider = roomId -> exits.getOrDefault(roomId, Map.of());
        Function<RoomId, String> names = roomId -> roomNames.getOrDefault(roomId, roomId.getValue());
        WayfindService wayfindService = new WayfindService(
            new EmptyAreaRepository(), new RoomPathfinder(), () -> List.copyOf(ferries), provider, names);
        return new CorpseLocatorService(wayfindService, names, () -> DECAY_SECONDS, () -> NOW);
    }

    private static Corpse corpse(String roomId, int gold, Instant spawnedAt) {
        return new Corpse(ItemId.of("corpse-hero-1"), RoomId.of(roomId), "Hero", gold, spawnedAt);
    }

    @Test
    void noCorpseReportsNoCorpse() {
        List<String> lines = service(List.of()).locate(RoomId.of("here"), null);

        assertEquals(List.of("You have no corpse in the world."), lines);
    }

    @Test
    void decayedButStillTrackedCorpseReportsNoCorpse() {
        Corpse decayed = corpse("dark-cave", 50, NOW.minusSeconds(DECAY_SECONDS + 10));

        List<String> lines = service(List.of()).locate(RoomId.of("here"), decayed);

        assertEquals(List.of("You have no corpse in the world."), lines);
    }

    @Test
    void corpseInCurrentRoomWithGoldConfirmsStandingOnIt() {
        Corpse here = corpse("here", 214, NOW.minusSeconds(30));

        List<String> lines = service(List.of()).locate(RoomId.of("here"), here);

        assertEquals(
            List.of("Your corpse lies here, holding 214 gold. Loot it before it decays!"),
            lines);
    }

    @Test
    void corpseInCurrentRoomWithoutGoldOmitsGoldClause() {
        Corpse here = corpse("here", 0, NOW.minusSeconds(30));

        List<String> lines = service(List.of()).locate(RoomId.of("here"), here);

        assertEquals(
            List.of("Your corpse lies here. Loot it before it decays!"),
            lines);
    }

    @Test
    void corpseElsewhereReportsRoomGoldTimeAndDirections() {
        corridor("here", Direction.NORTH, "mid");
        corridor("mid", Direction.EAST, "dark-cave");
        roomNames.put(RoomId.of("dark-cave"), "The Dark Cave");
        Corpse elsewhere = corpse("dark-cave", 214, NOW.minusSeconds(120));

        List<String> lines = service(List.of()).locate(RoomId.of("here"), elsewhere);

        assertEquals(2, lines.size());
        assertEquals(
            "Your corpse lies in The Dark Cave, holding 214 gold. It will decay in about 3 minutes.",
            lines.get(0));
        assertEquals("The Dark Cave is 2 step(s) away: north, east.", lines.get(1));
    }

    @Test
    void corpseElsewhereWithoutGoldOmitsGoldClause() {
        corridor("here", Direction.EAST, "dark-cave");
        roomNames.put(RoomId.of("dark-cave"), "The Dark Cave");
        Corpse elsewhere = corpse("dark-cave", 0, NOW.minusSeconds(DECAY_SECONDS - 30));

        List<String> lines = service(List.of()).locate(RoomId.of("here"), elsewhere);

        assertEquals(
            "Your corpse lies in The Dark Cave. It will decay in less than a minute.",
            lines.get(0));
        assertEquals("The Dark Cave is 1 step(s) away: east.", lines.get(1));
    }

    @Test
    void unreachableCorpseRoomReportsNoKnownRoute() {
        roomNames.put(RoomId.of("lost-crypt"), "The Lost Crypt");
        Corpse elsewhere = corpse("lost-crypt", 10, NOW.minusSeconds(60));

        List<String> lines = service(List.of()).locate(RoomId.of("here"), elsewhere);

        assertEquals(
            "Your corpse lies in The Lost Crypt, holding 10 gold. It will decay in about 4 minutes.",
            lines.get(0));
        assertEquals("No known route to The Lost Crypt.", lines.get(1));
    }

    @Test
    void corpseAcrossAFerryRendersTheFerryLeg() {
        // No walking path reaches the island; the only way across is the Coastal Ferry.
        corridor("here", Direction.NORTH, "north-dock");
        corridor("south-dock", Direction.EAST, "isle-shore");
        roomNames.put(RoomId.of("north-dock"), "North Dock");
        roomNames.put(RoomId.of("south-dock"), "South Dock");
        roomNames.put(RoomId.of("isle-shore"), "Isle Shore");
        Ferry ferry = new Ferry(
            FerryId.of("coastal"),
            "Coastal Ferry",
            RoomId.of("coastal-ferry-deck"),
            List.of(RoomId.of("north-dock"), RoomId.of("south-dock")),
            15,
            0,
            List.of(),
            List.of());
        Corpse elsewhere = corpse("isle-shore", 5, NOW.minusSeconds(60));

        List<String> lines = service(List.of(ferry)).locate(RoomId.of("here"), elsewhere);

        assertEquals(
            "Isle Shore is 2 step(s) away: north, then board the Coastal Ferry at North Dock "
                + "and ride to South Dock, then east.",
            lines.get(1));
    }

    private record EmptyAreaRepository() implements AreaRepository {
        @Override
        public List<Area> findAll() {
            return List.of();
        }

        @Override
        public Optional<Area> findById(AreaId id) {
            return Optional.empty();
        }

        @Override
        public Optional<WorldAtlas> findAtlas() {
            return Optional.empty();
        }
    }
}
