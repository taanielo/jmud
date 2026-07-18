package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

class RoomPathfinderTest {

    private final RoomPathfinder pathfinder = new RoomPathfinder();

    /** A mutable in-memory room graph exposed as the pathfinder's walkable-exit provider. */
    private static final class Graph {
        private final Map<RoomId, Map<Direction, RoomId>> exits = new HashMap<>();

        Graph link(String from, Direction direction, String to) {
            exits.computeIfAbsent(RoomId.of(from), id -> new LinkedHashMap<>())
                .put(direction, RoomId.of(to));
            return this;
        }

        /** Adds a two-way corridor: {@code from -dir-> to} and {@code to -opposite-> from}. */
        Graph corridor(String from, Direction direction, String to) {
            return link(from, direction, to).link(to, direction.opposite(), from);
        }

        Map<Direction, RoomId> exitsOf(RoomId roomId) {
            return exits.getOrDefault(roomId, Map.of());
        }
    }

    @Test
    void sameStartAndDestinationIsAZeroStepRoute() {
        Optional<List<Direction>> path = pathfinder.findPath(
            RoomId.of("a"), RoomId.of("a"), new Graph()::exitsOf);

        assertTrue(path.isPresent());
        assertEquals(List.of(), path.get());
    }

    @Test
    void adjacentDestinationIsASingleHop() {
        Graph graph = new Graph().corridor("a", Direction.NORTH, "b");

        Optional<List<Direction>> path = pathfinder.findPath(
            RoomId.of("a"), RoomId.of("b"), graph::exitsOf);

        assertTrue(path.isPresent());
        assertEquals(List.of(Direction.NORTH), path.get());
    }

    @Test
    void multiHopStraightLineReturnsEveryStepInOrder() {
        Graph graph = new Graph()
            .corridor("a", Direction.NORTH, "b")
            .corridor("b", Direction.NORTH, "c")
            .corridor("c", Direction.UP, "d")
            .corridor("d", Direction.EAST, "e");

        Optional<List<Direction>> path = pathfinder.findPath(
            RoomId.of("a"), RoomId.of("e"), graph::exitsOf);

        assertTrue(path.isPresent());
        assertEquals(
            List.of(Direction.NORTH, Direction.NORTH, Direction.UP, Direction.EAST),
            path.get());
    }

    @Test
    void branchingGraphChoosesTheShortestPath() {
        // Long way: a -> b -> c -> d (3 hops). Short way: a -> d directly (1 hop).
        Graph graph = new Graph()
            .corridor("a", Direction.NORTH, "b")
            .corridor("b", Direction.NORTH, "c")
            .corridor("c", Direction.NORTH, "d")
            .corridor("a", Direction.EAST, "d");

        Optional<List<Direction>> path = pathfinder.findPath(
            RoomId.of("a"), RoomId.of("d"), graph::exitsOf);

        assertTrue(path.isPresent());
        assertEquals(List.of(Direction.EAST), path.get());
    }

    @Test
    void unreachableDestinationYieldsNoPath() {
        // Two disconnected components: {a,b} and {island}.
        Graph graph = new Graph()
            .corridor("a", Direction.NORTH, "b")
            .link("island", Direction.SOUTH, "island");

        Optional<List<Direction>> path = pathfinder.findPath(
            RoomId.of("a"), RoomId.of("island"), graph::exitsOf);

        assertTrue(path.isEmpty());
    }

    @Test
    void hiddenExitIsExcludedUntilDiscoveredThenIncluded() {
        Set<RoomId> discoveredRooms = new HashSet<>();
        RoomId start = RoomId.of("cave");
        RoomId secretDest = RoomId.of("vault");
        // Regular exits reach nothing useful; only the hidden exit (once discovered) reaches the vault.
        Function<RoomId, Map<Direction, RoomId>> visibleExits = roomId -> {
            if (roomId.equals(start) && discoveredRooms.contains(start)) {
                return Map.of(Direction.DOWN, secretDest);
            }
            return Map.of();
        };

        Optional<List<Direction>> beforeDiscovery =
            pathfinder.findPath(start, secretDest, visibleExits);
        assertTrue(beforeDiscovery.isEmpty());

        discoveredRooms.add(start);

        Optional<List<Direction>> afterDiscovery =
            pathfinder.findPath(start, secretDest, visibleExits);
        assertFalse(afterDiscovery.isEmpty());
        assertEquals(List.of(Direction.DOWN), afterDiscovery.get());
    }
}
