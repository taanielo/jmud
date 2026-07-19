package io.taanielo.jmud.core.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Pure, network-free domain service that finds the shortest walking route between two rooms.
 *
 * <p>The pathfinder performs a breadth-first search over the room-exit graph, following only the
 * exits a caller declares walkable via the supplied exit provider. In production the provider is
 * {@code PlayerLocationService::getVisibleExits}, i.e. a room's regular exits plus any hidden exits
 * that have already been globally discovered (via SEARCH) — an undiscovered secret door is never
 * part of the graph and so is never spoiled. Locked doors ARE walkable here: a locked door still
 * leads somewhere, and carrying the right key is a separate mechanic, so routes are not broken by a
 * lock.
 *
 * <p>The service is stateless and holds no game state, so it is safe to call on the tick thread
 * (AGENTS.md §5) and trivially unit-testable by passing an in-memory exit provider. The search is
 * bounded by {@link #MAX_ROOMS_EXPLORED} so a pathological or cyclic world graph can never stall the
 * tick.
 */
public class RoomPathfinder {

    /**
     * Upper bound on rooms expanded during a single search, guarding the tick loop against a
     * runaway walk of a pathological world graph. The world is far smaller than this today.
     */
    private static final int MAX_ROOMS_EXPLORED = 10_000;

    /**
     * Canonical direction order used to break ties between equally-short routes, giving BFS a
     * deterministic, run-independent expansion order without depending on {@link Enum#ordinal()}.
     * Mirrors the {@link Direction} declaration order (north, south, east, west, up, down).
     */
    private static final List<Direction> CANONICAL_ORDER =
            List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN);

    /**
     * Finds the shortest sequence of compass directions that walks from {@code start} to
     * {@code destination}, using only the exits reported by {@code walkableExits}.
     *
     * <p>When {@code start} equals {@code destination} the route is an empty list (the traveller is
     * already there). Among several equally-short routes a deterministic one is chosen by expanding
     * each room's exits in {@link Direction} declaration order, so the same graph always yields the
     * same directions.
     *
     * @param start         the room the traveller starts in
     * @param destination   the room the traveller wants to reach
     * @param walkableExits a function returning, for any room id, its walkable exits keyed by the
     *                      direction that traverses them
     * @return the ordered list of directions to walk (empty when already at the destination), or
     *         {@link Optional#empty()} when no walking route exists
     */
    public Optional<List<Direction>> findPath(
            RoomId start,
            RoomId destination,
            Function<RoomId, Map<Direction, RoomId>> walkableExits) {
        Objects.requireNonNull(start, "Start room id is required");
        Objects.requireNonNull(destination, "Destination room id is required");
        Objects.requireNonNull(walkableExits, "Walkable exits provider is required");

        if (start.equals(destination)) {
            return Optional.of(List.of());
        }

        Deque<RoomId> frontier = new ArrayDeque<>();
        Map<RoomId, RoomId> cameFrom = new HashMap<>();
        Map<RoomId, Direction> arrivedVia = new HashMap<>();
        Set<RoomId> visited = new HashSet<>();
        frontier.add(start);
        visited.add(start);
        int explored = 0;

        while (!frontier.isEmpty() && explored < MAX_ROOMS_EXPLORED) {
            RoomId current = frontier.removeFirst();
            explored++;
            for (Map.Entry<Direction, RoomId> exit : sortedExits(walkableExits.apply(current))) {
                RoomId neighbour = exit.getValue();
                if (!visited.add(neighbour)) {
                    continue;
                }
                cameFrom.put(neighbour, current);
                arrivedVia.put(neighbour, exit.getKey());
                if (neighbour.equals(destination)) {
                    return Optional.of(reconstruct(start, destination, cameFrom, arrivedVia));
                }
                frontier.add(neighbour);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns a room's exits ordered by {@link Direction} declaration order, so BFS expansion — and
     * therefore the chosen shortest route among ties — is deterministic across runs.
     */
    private static List<Map.Entry<Direction, RoomId>> sortedExits(Map<Direction, RoomId> exits) {
        List<Map.Entry<Direction, RoomId>> ordered = new ArrayList<>(exits.entrySet());
        ordered.sort(Comparator.comparingInt(e -> CANONICAL_ORDER.indexOf(e.getKey())));
        return ordered;
    }

    /**
     * Walks the {@code cameFrom} chain back from the destination to the start, collecting the
     * direction taken to arrive at each room, then reverses it into start-to-destination order.
     */
    private static List<Direction> reconstruct(
            RoomId start,
            RoomId destination,
            Map<RoomId, RoomId> cameFrom,
            Map<RoomId, Direction> arrivedVia) {
        List<Direction> path = new ArrayList<>();
        RoomId cursor = destination;
        while (!cursor.equals(start)) {
            path.add(arrivedVia.get(cursor));
            cursor = cameFrom.get(cursor);
        }
        Collections.reverse(path);
        return List.copyOf(path);
    }
}
