package io.taanielo.jmud.core.world.area;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 * Read-only domain service backing the {@code WAYFIND} command: it turns "how do I walk to that
 * zone I've already visited?" into live, turn-by-turn compass directions.
 *
 * <p>Given a player's current room and an area name or id, it resolves the destination area, then
 * asks {@link RoomPathfinder} for the shortest walking route to that area's waypoint (its first
 * room, {@code room_ids[0]} — the same entrance convention BIND and {@link AreaWaypointService}
 * use). Routing only crosses exits the world graph already exposes for normal walking (regular
 * exits plus globally-discovered hidden exits); ferry hops and other non-{@link Direction} transport
 * are not part of the walkable graph, so a ferry-only destination is reported as such rather than
 * pretended reachable.
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

    /**
     * Creates a wayfinding service.
     *
     * @param areaRepository the source of area definitions used to resolve destinations and list
     *                       neighbours
     * @param pathfinder     the shortest-route search over the walkable room graph
     * @param ferryRepository the world's ferry definitions, used only to tell a ferry-only
     *                       destination apart from a truly unreachable one
     * @param walkableExits  a function returning, for any room, the exits that may be walked
     *                       (regular exits plus globally-discovered hidden exits)
     */
    public WayfindService(
            AreaRepository areaRepository,
            RoomPathfinder pathfinder,
            FerryRepository ferryRepository,
            Function<RoomId, Map<Direction, RoomId>> walkableExits) {
        this.areaRepository = Objects.requireNonNull(areaRepository, "Area repository is required");
        this.pathfinder = Objects.requireNonNull(pathfinder, "Room pathfinder is required");
        this.ferryRepository = Objects.requireNonNull(ferryRepository, "Ferry repository is required");
        this.walkableExits = Objects.requireNonNull(walkableExits, "Walkable exits provider is required");
    }

    /**
     * Produces the player-facing lines for a {@code WAYFIND} invocation.
     *
     * <p>With a blank query it lists the player's current area and its charted neighbours. With a
     * query it resolves the destination area (exact id, exact name, then partial match) and either
     * prints the turn-by-turn route to that area's waypoint, an ambiguity/unknown prompt, or a
     * friendly no-route message (distinguishing a ferry-only destination from a truly unreachable
     * one). It never throws for player input and never mutates game state.
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
        Optional<List<Direction>> route = pathfinder.findPath(currentRoom, waypoint, walkableExits);
        if (route.isPresent()) {
            return List.of(renderRoute(destination, route.get()));
        }
        if (reachableIncludingFerries(currentRoom, waypoint)) {
            return List.of("No walking route to " + destination.name()
                + " found (you may need to take a ferry).");
        }
        return List.of("No known route to " + destination.name() + ".");
    }

    /**
     * Formats a found route as the total step count and the comma-joined direction labels, or a
     * "you are already here" line when the route is empty.
     */
    private static String renderRoute(Area destination, List<Direction> directions) {
        if (directions.isEmpty()) {
            return "You are already at the entrance to " + destination.name() + ".";
        }
        String steps = directions.stream().map(Direction::label).collect(Collectors.joining(", "));
        return destination.name() + " is " + directions.size() + " step(s) away: " + steps + ".";
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
     * Returns whether the destination is reachable when ferry hops are allowed in addition to normal
     * walking. Used only to distinguish a ferry-only destination from a truly unreachable one; each
     * ferry contributes virtual edges from its deck room to every dock on its route, modelling the
     * ferry carrying a boarded passenger to another dock.
     */
    private boolean reachableIncludingFerries(RoomId start, RoomId destination) {
        if (start.equals(destination)) {
            return true;
        }
        Map<RoomId, List<RoomId>> ferryEdges = buildFerryEdges();
        Deque<RoomId> frontier = new ArrayDeque<>();
        Set<RoomId> visited = new HashSet<>();
        frontier.add(start);
        visited.add(start);
        while (!frontier.isEmpty()) {
            RoomId current = frontier.removeFirst();
            List<RoomId> neighbours = new ArrayList<>(walkableExits.apply(current).values());
            neighbours.addAll(ferryEdges.getOrDefault(current, List.of()));
            for (RoomId neighbour : neighbours) {
                if (neighbour.equals(destination)) {
                    return true;
                }
                if (visited.add(neighbour)) {
                    frontier.add(neighbour);
                }
            }
        }
        return false;
    }

    /** Builds the deck-room-to-docks virtual adjacency for every configured ferry. */
    private Map<RoomId, List<RoomId>> buildFerryEdges() {
        Map<RoomId, List<RoomId>> edges = new HashMap<>();
        for (Ferry ferry : ferryRepository.findAll()) {
            edges.computeIfAbsent(ferry.deckRoomId(), id -> new ArrayList<>()).addAll(ferry.route());
        }
        return edges;
    }
}
