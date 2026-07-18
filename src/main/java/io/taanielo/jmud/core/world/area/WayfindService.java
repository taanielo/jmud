package io.taanielo.jmud.core.world.area;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.transport.Ferry;
import io.taanielo.jmud.core.transport.FerryRepository;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomPathfinder;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Read-only domain service backing the {@code WAYFIND} command: it turns "how do I get to that zone
 * I've already visited?" into live, turn-by-turn directions.
 *
 * <p>Given a player's current room and an area name or id, it resolves the destination area, then
 * asks {@link RoomPathfinder} for the shortest walking route to that area's waypoint (its first
 * room, {@code room_ids[0]} — the same entrance convention BIND and {@link AreaWaypointService}
 * use). Walking routes cross only exits the world graph already exposes for normal walking (regular
 * exits plus globally-discovered hidden exits).
 *
 * <p>When the shortest (or only) route requires boarding a ferry, the route is stitched together
 * from two walking legs — start to a boarding dock and an arrival dock to the destination — with a
 * distinct "board the ferry" step spliced between them. Only routes crossing at most one ferry leg
 * are considered; there is exactly one ferry in the world today (a two-dock route), so multi-ferry
 * chaining is intentionally out of scope. Among the pure-walking route and every candidate ferry
 * route, the one with the fewest total steps wins, with ties broken deterministically in favour of
 * the pure-walking route and then the ferry-repository/route-index order.
 *
 * <p>Every lookup is an in-memory repository read with no mutation, safe to run on the tick thread
 * (AGENTS.md §5).
 */
@Slf4j
public class WayfindService {

    private final AreaRepository areaRepository;
    private final RoomPathfinder pathfinder;
    private final FerryRepository ferryRepository;
    private final Function<RoomId, Map<Direction, RoomId>> walkableExits;
    private final Function<RoomId, String> roomNames;

    /**
     * Creates a wayfinding service.
     *
     * @param areaRepository  the source of area definitions used to resolve destinations and list
     *                        neighbours
     * @param pathfinder      the shortest-route search over the walkable room graph
     * @param ferryRepository the world's ferry definitions, used to route across ferry crossings and
     *                        to render their boarding docks
     * @param walkableExits   a function returning, for any room, the exits that may be walked
     *                        (regular exits plus globally-discovered hidden exits)
     * @param roomNames       a function returning a room's display name, used to name the boarding
     *                        and arrival docks in a ferry step
     */
    public WayfindService(
            AreaRepository areaRepository,
            RoomPathfinder pathfinder,
            FerryRepository ferryRepository,
            Function<RoomId, Map<Direction, RoomId>> walkableExits,
            Function<RoomId, String> roomNames) {
        this.areaRepository = Objects.requireNonNull(areaRepository, "Area repository is required");
        this.pathfinder = Objects.requireNonNull(pathfinder, "Room pathfinder is required");
        this.ferryRepository = Objects.requireNonNull(ferryRepository, "Ferry repository is required");
        this.walkableExits = Objects.requireNonNull(walkableExits, "Walkable exits provider is required");
        this.roomNames = Objects.requireNonNull(roomNames, "Room name resolver is required");
    }

    /**
     * Produces the player-facing lines for a {@code WAYFIND} invocation.
     *
     * <p>With a blank query it lists the player's current area and its charted neighbours. With a
     * query it resolves the destination area (exact id, exact name, then partial match) and either
     * prints the turn-by-turn route to that area's waypoint (walking, or walking plus a single ferry
     * leg when that is the shortest or only way), an ambiguity/unknown prompt, or a friendly
     * no-route message. It never throws for player input and never mutates game state.
     *
     * @param currentRoom the room the player is standing in
     * @param rawQuery    the raw argument after {@code WAYFIND} (blank lists nearby areas)
     * @return one or more lines to send to the player (never empty, never {@code null})
     */
    public List<String> wayfind(RoomId currentRoom, @Nullable String rawQuery) {
        Objects.requireNonNull(currentRoom, "Current room is required");
        List<Area> areas;
        try {
            areas = areaRepository.findAll();
        } catch (RepositoryException e) {
            log.warn("WAYFIND could not read area data: {}", e.getMessage());
            return List.of("You cannot get your bearings right now.");
        }

        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) {
            return describeNeighbours(currentRoom, areas);
        }
        return routeTo(currentRoom, query, areas);
    }

    /**
     * Lists the player's current area and the areas it directly connects to (its atlas neighbours),
     * as a lightweight "where can I even route to" hint.
     */
    private List<String> describeNeighbours(RoomId currentRoom, List<Area> areas) {
        Area current = findAreaContaining(currentRoom, areas);
        if (current == null) {
            return List.of("You cannot get your bearings here.");
        }
        if (current.connections().isEmpty()) {
            return List.of("You are in " + current.name() + ". It has no charted neighbours.");
        }
        String neighbours = current.connections().stream()
            .map(id -> areaName(id, areas))
            .collect(Collectors.joining(", "));
        return List.of("You are in " + current.name()
            + ". From here you can route toward: " + neighbours + ".");
    }

    /**
     * Resolves the destination area from the query and renders the route (or the appropriate
     * ambiguity/unknown/no-route message) to it.
     */
    private List<String> routeTo(RoomId currentRoom, String query, List<Area> areas) {
        String needle = query.toLowerCase(Locale.ROOT);

        Area exact = findExactMatch(needle, areas);
        List<Area> matches;
        if (exact != null) {
            matches = List.of(exact);
        } else {
            matches = areas.stream()
                .filter(area -> area.name().toLowerCase(Locale.ROOT).contains(needle)
                    || area.id().getValue().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        }

        if (matches.isEmpty()) {
            return List.of("Unknown area — try WAYFIND with no arguments to see nearby areas.");
        }
        if (matches.size() > 1) {
            String names = matches.stream().map(Area::name).collect(Collectors.joining(", "));
            return List.of("Which area did you mean? Be more specific: " + names + ".");
        }

        Area destination = matches.get(0);
        RoomId waypoint = destination.roomIds().get(0);
        Optional<List<RouteStep>> route = findBestRoute(currentRoom, waypoint);
        if (route.isPresent()) {
            return List.of(renderRoute(destination.name(), route.get()));
        }
        return List.of("No known route to " + destination.name() + ".");
    }

    /**
     * Renders ferry-aware turn-by-turn directions between two arbitrary rooms, phrased for a named
     * destination. Shared by the {@code CORPSE} command so a fallen player can be routed back to
     * their remains with the exact same walking + single-ferry-leg routing {@code WAYFIND} uses.
     *
     * <p>The returned sentence has the same shape as a {@code WAYFIND} route line
     * ("{@code <name> is N step(s) away: ...}"), or the "you are already at" line when the two rooms
     * coincide. An empty result means no walking-or-ferry route connects the two rooms. This is a
     * pure in-memory read with no mutation, safe on the tick thread (AGENTS.md §5).
     *
     * @param destinationName the display name to use for the destination in the rendered sentence
     * @param from            the room to route from (the player's current room)
     * @param to              the destination room
     * @return the rendered directions sentence, or empty when no route exists
     */
    public Optional<String> describeRoute(String destinationName, RoomId from, RoomId to) {
        Objects.requireNonNull(destinationName, "Destination name is required");
        Objects.requireNonNull(from, "Origin room is required");
        Objects.requireNonNull(to, "Destination room is required");
        return findBestRoute(from, to).map(steps -> renderRoute(destinationName, steps));
    }

    /**
     * Finds the shortest route to the waypoint, considering the pure-walking route and every route
     * that crosses a single ferry leg (walk to a boarding dock, board, walk from the arrival dock).
     *
     * <p>Cost is the total number of steps: each walked direction counts as one, and a ferry leg
     * counts as one. The pure-walking route is preferred on ties (so a walkable destination renders
     * exactly as before), then earlier ferries and route-index pairs, giving deterministic output.
     */
    private Optional<List<RouteStep>> findBestRoute(RoomId currentRoom, RoomId waypoint) {
        List<RouteStep> best = null;
        int bestCost = Integer.MAX_VALUE;

        Optional<List<Direction>> walk = pathfinder.findPath(currentRoom, waypoint, walkableExits);
        if (walk.isPresent()) {
            best = walk.get().stream().map(WayfindService::walkStep).toList();
            bestCost = best.size();
        }

        for (Ferry ferry : ferryRepository.findAll()) {
            List<RoomId> docks = ferry.route();
            for (int i = 0; i < docks.size(); i++) {
                for (int j = 0; j < docks.size(); j++) {
                    if (i == j) {
                        continue;
                    }
                    RoomId boardingDock = docks.get(i);
                    RoomId arrivalDock = docks.get(j);
                    Optional<List<Direction>> legToDock = pathfinder.findPath(currentRoom, boardingDock, walkableExits);
                    if (legToDock.isEmpty()) {
                        continue;
                    }
                    Optional<List<Direction>> legFromDock = pathfinder.findPath(arrivalDock, waypoint, walkableExits);
                    if (legFromDock.isEmpty()) {
                        continue;
                    }
                    int cost = legToDock.get().size() + legFromDock.get().size() + 1;
                    if (cost < bestCost) {
                        best = stitchFerryRoute(ferry, boardingDock, arrivalDock, legToDock.get(), legFromDock.get());
                        bestCost = cost;
                    }
                }
            }
        }

        return Optional.ofNullable(best);
    }

    /**
     * Assembles a ferry route from the walk to the boarding dock, the ferry leg, and the walk from
     * the arrival dock.
     */
    private List<RouteStep> stitchFerryRoute(
            Ferry ferry,
            RoomId boardingDock,
            RoomId arrivalDock,
            List<Direction> legToDock,
            List<Direction> legFromDock) {
        List<RouteStep> steps = new ArrayList<>();
        legToDock.forEach(direction -> steps.add(walkStep(direction)));
        steps.add(new FerryStep(ferry.name(), roomNames.apply(boardingDock), roomNames.apply(arrivalDock)));
        legFromDock.forEach(direction -> steps.add(walkStep(direction)));
        return List.copyOf(steps);
    }

    private static RouteStep walkStep(Direction direction) {
        return new WalkStep(direction);
    }

    /**
     * Formats a found route as the total step count and the ordered step phrases, or a "you are
     * already here" line when the route is empty. Walked directions are comma-joined exactly as
     * before; a ferry leg is rendered as a distinct, plainly-worded step introduced (and followed)
     * by "then" so it can never be mistaken for a compass direction.
     */
    private static String renderRoute(String destinationName, List<RouteStep> steps) {
        if (steps.isEmpty()) {
            return "You are already at the entrance to " + destinationName + ".";
        }
        long walkCount = steps.stream().filter(step -> step instanceof WalkStep).count();
        StringBuilder joined = new StringBuilder();
        boolean previousWasFerry = false;
        for (int i = 0; i < steps.size(); i++) {
            RouteStep step = steps.get(i);
            boolean isFerry = step instanceof FerryStep;
            if (i > 0) {
                joined.append(", ");
                if (isFerry || previousWasFerry) {
                    joined.append("then ");
                }
            }
            joined.append(step.label());
            previousWasFerry = isFerry;
        }
        return destinationName + " is " + walkCount + " step(s) away: " + joined + ".";
    }

    /**
     * Returns the area whose name or id exactly equals the (already lower-cased) query, preferring an
     * id match, or {@code null} when none does. Keeps {@code WAYFIND frozen-peaks} and
     * {@code WAYFIND Frozen Peaks} both landing on the same area even when a partial match would be
     * ambiguous.
     */
    @Nullable
    private static Area findExactMatch(String needle, List<Area> areas) {
        for (Area area : areas) {
            if (area.id().getValue().toLowerCase(Locale.ROOT).equals(needle)) {
                return area;
            }
        }
        for (Area area : areas) {
            if (area.name().toLowerCase(Locale.ROOT).equals(needle)) {
                return area;
            }
        }
        return null;
    }

    /** Returns the area that contains the given room, or {@code null} when no area claims it. */
    @Nullable
    private static Area findAreaContaining(RoomId roomId, List<Area> areas) {
        for (Area area : areas) {
            if (area.roomIds().contains(roomId)) {
                return area;
            }
        }
        return null;
    }

    /** Resolves an area id to its display name, falling back to the raw id when it does not resolve. */
    private static String areaName(AreaId id, List<Area> areas) {
        for (Area area : areas) {
            if (area.id().equals(id)) {
                return area.name();
            }
        }
        return id.getValue();
    }

    /**
     * A single leg of a rendered route: either a walked compass direction or a ferry crossing.
     */
    private sealed interface RouteStep permits WalkStep, FerryStep {
        /** The player-facing phrase for this step. */
        String label();
    }

    /** A single walked compass move. */
    private record WalkStep(Direction direction) implements RouteStep {
        @Override
        public String label() {
            return direction.label();
        }
    }

    /** A single ferry crossing from one dock to another aboard a named ferry. */
    private record FerryStep(String ferryName, String boardingDock, String arrivalDock) implements RouteStep {
        @Override
        public String label() {
            return "board the " + ferryName + " at " + boardingDock + " and ride to " + arrivalDock;
        }
    }
}
