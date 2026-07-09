package io.taanielo.jmud.core.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Domain service that renders a small ASCII minimap of the rooms surrounding a player.
 *
 * <p>Rooms in jmud are not laid out on a coordinate grid; they form an exit graph
 * ({@link Direction} &rarr; {@link RoomId}). This service therefore derives a 2D layout by
 * breadth-first traversal from the player's current room, assigning grid offsets from the cardinal
 * exit directions (north/south/east/west). Vertical exits (up/down) have no 2D position and are not
 * placed on the grid.
 *
 * <p>A player only ever sees rooms they have previously visited (their
 * {@link io.taanielo.jmud.core.player.PlayerExploration exploration} set). Traversal only expands
 * through explored rooms; unexplored rooms directly adjacent to an explored room are shown as a
 * frontier marker so the player can tell where unexplored territory lies.
 *
 * <p>All rendering runs synchronously on the tick thread as part of command execution
 * (AGENTS.md &sect;5); it performs no mutation and only reads room data through the injected
 * {@link RoomRepository}.
 */
public class MapService {

    /** Half-width of the rendered grid; radius 2 yields a 5&times;5 view. */
    private static final int RADIUS = 2;

    private static final char PLAYER = '@';
    private static final char EXPLORED = '#';
    private static final char FRONTIER = '.';
    private static final char EMPTY = ' ';

    private final RoomRepository roomRepository;

    /**
     * Creates a map service backed by the given room repository (the world's room graph).
     *
     * @param roomRepository the repository used to resolve rooms and their exits
     */
    public MapService(RoomRepository roomRepository) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "Room repository is required");
    }

    /**
     * Renders an ASCII minimap centred on {@code currentRoom}, showing explored rooms within
     * {@value #RADIUS} tiles.
     *
     * <p>Symbols: {@code @} the player's current room, {@code #} an explored room, {@code .} an
     * unexplored room adjacent to explored territory, and a blank for empty space.
     *
     * @param player      the player whose exploration set determines what is visible
     * @param currentRoom the player's current room id, always drawn as {@code @}
     * @return a multi-line ASCII map, one line per grid row plus a legend
     */
    public String renderMap(Player player, RoomId currentRoom) {
        Objects.requireNonNull(player, "Player is required");
        Objects.requireNonNull(currentRoom, "Current room is required");

        Map<Point, Character> grid = new HashMap<>();
        Map<Point, RoomId> placedRooms = new HashMap<>();
        grid.put(Point.ORIGIN, PLAYER);
        placedRooms.put(Point.ORIGIN, currentRoom);

        Deque<Placement> queue = new ArrayDeque<>();
        queue.add(new Placement(currentRoom, Point.ORIGIN));

        while (!queue.isEmpty()) {
            Placement placement = queue.poll();
            Room room = findRoom(placement.roomId());
            if (room == null) {
                continue;
            }
            for (Map.Entry<Direction, RoomId> exit : room.getExits().entrySet()) {
                Point offset = offsetFor(exit.getKey());
                if (offset == null) {
                    continue;
                }
                Point neighbour = placement.point().translate(offset);
                if (neighbour.outsideRadius(RADIUS) || placedRooms.containsKey(neighbour)) {
                    continue;
                }
                RoomId neighbourId = exit.getValue();
                boolean explored = player.exploration().hasVisited(neighbourId);
                if (explored) {
                    grid.put(neighbour, EXPLORED);
                    placedRooms.put(neighbour, neighbourId);
                    queue.add(new Placement(neighbourId, neighbour));
                } else if (!grid.containsKey(neighbour)) {
                    grid.put(neighbour, FRONTIER);
                }
            }
        }

        return render(grid);
    }

    private String render(Map<Point, Character> grid) {
        List<String> lines = new ArrayList<>();
        lines.add("Map of your surroundings:");
        for (int y = RADIUS; y >= -RADIUS; y--) {
            StringBuilder row = new StringBuilder();
            for (int x = -RADIUS; x <= RADIUS; x++) {
                if (x > -RADIUS) {
                    row.append(' ');
                }
                row.append(grid.getOrDefault(new Point(x, y), EMPTY));
            }
            lines.add(row.toString());
        }
        lines.add("Legend: @ you  # explored  . unexplored");
        return String.join("\n", lines);
    }

    private @Nullable Room findRoom(RoomId roomId) {
        try {
            return roomRepository.findById(roomId).orElse(null);
        } catch (RepositoryException e) {
            return null;
        }
    }

    private static @Nullable Point offsetFor(Direction direction) {
        return switch (direction) {
            case NORTH -> new Point(0, 1);
            case SOUTH -> new Point(0, -1);
            case EAST -> new Point(1, 0);
            case WEST -> new Point(-1, 0);
            case UP, DOWN -> null;
        };
    }

    /** A room queued for exit expansion at a grid position. */
    private record Placement(RoomId roomId, Point point) {
    }

    /** An immutable integer grid coordinate; {@code y} increases northward. */
    private record Point(int x, int y) {
        static final Point ORIGIN = new Point(0, 0);

        Point translate(Point offset) {
            return new Point(x + offset.x, y + offset.y);
        }

        boolean outsideRadius(int radius) {
            return Math.abs(x) > radius || Math.abs(y) > radius;
        }
    }
}
