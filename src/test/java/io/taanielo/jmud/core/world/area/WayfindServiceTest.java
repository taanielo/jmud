package io.taanielo.jmud.core.world.area;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.transport.Ferry;
import io.taanielo.jmud.core.transport.FerryId;
import io.taanielo.jmud.core.transport.FerryRepository;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomPathfinder;

class WayfindServiceTest {

    private final Map<RoomId, Map<Direction, RoomId>> exits = new HashMap<>();

    private void corridor(String from, Direction direction, String to) {
        exits.computeIfAbsent(RoomId.of(from), id -> new LinkedHashMap<>()).put(direction, RoomId.of(to));
        exits.computeIfAbsent(RoomId.of(to), id -> new LinkedHashMap<>())
            .put(direction.opposite(), RoomId.of(from));
    }

    private void oneWay(String from, Direction direction, String to) {
        exits.computeIfAbsent(RoomId.of(from), id -> new LinkedHashMap<>()).put(direction, RoomId.of(to));
    }

    private WayfindService service(List<Area> areas, List<Ferry> ferries) {
        Function<RoomId, Map<Direction, RoomId>> provider = roomId -> exits.getOrDefault(roomId, Map.of());
        return new WayfindService(
            new StubAreaRepository(areas), new RoomPathfinder(), () -> List.copyOf(ferries), provider);
    }

    private static Area area(String id, String name, List<String> connections, String... roomIds) {
        return new Area(
            AreaId.of(id),
            name,
            List.of(roomIds).stream().map(RoomId::of).toList(),
            connections.stream().map(AreaId::of).toList(),
            List.of(name + " ART"),
            new LevelRange(1, 5));
    }

    @Test
    void noArgumentListsCurrentAreaAndNeighbours() {
        Area darkwood = area("darkwood", "Darkwood Wilds", List.of("town", "peaks"), "darkwood-trail");
        Area town = area("town", "Greystone Town", List.of(), "town-square");
        Area peaks = area("peaks", "Frozen Peaks", List.of(), "peaks-gate");
        WayfindService service = service(List.of(darkwood, town, peaks), List.of());

        List<String> lines = service.wayfind(RoomId.of("darkwood-trail"), "");

        assertEquals(1, lines.size());
        assertEquals(
            "You are in Darkwood Wilds. From here you can route toward: Greystone Town, Frozen Peaks.",
            lines.get(0));
    }

    @Test
    void routesToDestinationWaypointWithStepCountAndDirections() {
        corridor("darkwood-trail", Direction.NORTH, "clearing");
        corridor("clearing", Direction.UP, "peaks-gate");
        Area darkwood = area("darkwood", "Darkwood Wilds", List.of("peaks"), "darkwood-trail", "clearing");
        Area peaks = area("peaks", "Frozen Peaks", List.of(), "peaks-gate");
        WayfindService service = service(List.of(darkwood, peaks), List.of());

        List<String> lines = service.wayfind(RoomId.of("darkwood-trail"), "frozen peaks");

        assertEquals("Frozen Peaks is 2 step(s) away: north, up.", lines.get(0));
    }

    @Test
    void resolvesByAreaIdAndIsCaseInsensitive() {
        corridor("darkwood-trail", Direction.EAST, "peaks-gate");
        Area darkwood = area("darkwood", "Darkwood Wilds", List.of(), "darkwood-trail");
        Area peaks = area("peaks", "Frozen Peaks", List.of(), "peaks-gate");
        WayfindService service = service(List.of(darkwood, peaks), List.of());

        List<String> lines = service.wayfind(RoomId.of("darkwood-trail"), "PEAKS");

        assertEquals("Frozen Peaks is 1 step(s) away: east.", lines.get(0));
    }

    @Test
    void ambiguousPartialMatchAsksToBeMoreSpecific() {
        Area frost = area("frostwood", "Frostwood", List.of(), "frost-1");
        Area frozen = area("frozen", "Frozen Peaks", List.of(), "frozen-1");
        Area here = area("here", "Starting Glade", List.of(), "glade");
        WayfindService service = service(List.of(frost, frozen, here), List.of());

        List<String> lines = service.wayfind(RoomId.of("glade"), "fro");

        assertTrue(lines.get(0).startsWith("Which area did you mean? Be more specific:"));
        assertTrue(lines.get(0).contains("Frostwood"));
        assertTrue(lines.get(0).contains("Frozen Peaks"));
    }

    @Test
    void unknownAreaPromptsForNearbyAreas() {
        Area here = area("here", "Starting Glade", List.of(), "glade");
        WayfindService service = service(List.of(here), List.of());

        List<String> lines = service.wayfind(RoomId.of("glade"), "atlantis");

        assertEquals(
            "Unknown area — try WAYFIND with no arguments to see nearby areas.",
            lines.get(0));
    }

    @Test
    void alreadyAtWaypointReportsSo() {
        Area peaks = area("peaks", "Frozen Peaks", List.of(), "peaks-gate");
        WayfindService service = service(List.of(peaks), List.of());

        List<String> lines = service.wayfind(RoomId.of("peaks-gate"), "peaks");

        assertEquals("You are already at the entrance to Frozen Peaks.", lines.get(0));
    }

    @Test
    void ferryOnlyDestinationSuggestsTakingAFerry() {
        // The dock reaches the deck by walking, but only the ferry carries you across to the island.
        corridor("north-dock", Direction.DOWN, "ferry-deck");
        Area mainland = area("mainland", "Mainland", List.of(), "north-dock");
        Area island = area("island", "Shrouded Isle", List.of(), "isle-shore");
        Ferry ferry = new Ferry(
            FerryId.of("coastal"),
            "Coastal Ferry",
            RoomId.of("ferry-deck"),
            List.of(RoomId.of("north-dock"), RoomId.of("isle-shore")),
            15,
            0,
            List.of(),
            List.of());
        WayfindService service = service(List.of(mainland, island), List.of(ferry));

        List<String> lines = service.wayfind(RoomId.of("north-dock"), "shrouded isle");

        assertEquals(
            "No walking route to Shrouded Isle found (you may need to take a ferry).",
            lines.get(0));
    }

    @Test
    void trulyUnreachableDestinationReportsNoKnownRoute() {
        Area here = area("here", "Starting Glade", List.of(), "glade");
        Area island = area("island", "Lost Island", List.of(), "island-shore");
        WayfindService service = service(List.of(here, island), List.of());

        List<String> lines = service.wayfind(RoomId.of("glade"), "lost island");

        assertEquals("No known route to Lost Island.", lines.get(0));
    }

    @Test
    void ferryReachabilityDoesNotFalselyFlagAWalkableRoute() {
        // Sanity: an unrelated ferry must not turn a truly-unreachable destination into a ferry hint.
        oneWay("glade", Direction.NORTH, "dead-end");
        Area here = area("here", "Starting Glade", List.of(), "glade", "dead-end");
        Area island = area("island", "Lost Island", List.of(), "island-shore");
        Ferry unrelated = new Ferry(
            FerryId.of("other"),
            "Other Ferry",
            RoomId.of("other-deck"),
            List.of(RoomId.of("dock-x"), RoomId.of("dock-y")),
            10,
            0,
            List.of(),
            List.of());
        WayfindService service = service(List.of(here, island), List.of(unrelated));

        List<String> lines = service.wayfind(RoomId.of("glade"), "lost island");

        assertEquals("No known route to Lost Island.", lines.get(0));
    }

    private record StubAreaRepository(List<Area> areas) implements AreaRepository {
        @Override
        public List<Area> findAll() {
            return List.copyOf(areas);
        }

        @Override
        public Optional<Area> findById(AreaId id) {
            return areas.stream().filter(a -> a.id().equals(id)).findFirst();
        }

        @Override
        public Optional<WorldAtlas> findAtlas() {
            return Optional.empty();
        }
    }
}
