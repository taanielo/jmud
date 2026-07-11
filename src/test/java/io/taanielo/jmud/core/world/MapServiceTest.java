package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

class MapServiceTest {

    private static final RoomId CENTER = RoomId.of("center");
    private static final RoomId NORTH = RoomId.of("north");
    private static final RoomId SOUTH = RoomId.of("south");
    private static final RoomId EAST = RoomId.of("east");
    private static final RoomId WEST = RoomId.of("west");
    private static final RoomId FAR_NORTH = RoomId.of("far-north");

    private static final class MapRoomRepository implements RoomRepository {
        private final Map<RoomId, Room> rooms = new HashMap<>();

        void add(Room room) {
            rooms.put(room.getId(), room);
        }

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

    private static Room room(RoomId id, Map<Direction, RoomId> exits) {
        return new Room(id, id.getValue(), "A room.", exits, List.of(), List.of());
    }

    private MapRoomRepository crossWorld() {
        MapRoomRepository repo = new MapRoomRepository();
        repo.add(room(CENTER, Map.of(
            Direction.NORTH, NORTH,
            Direction.SOUTH, SOUTH,
            Direction.EAST, EAST,
            Direction.WEST, WEST)));
        repo.add(room(NORTH, Map.of(Direction.SOUTH, CENTER, Direction.NORTH, FAR_NORTH)));
        repo.add(room(SOUTH, Map.of(Direction.NORTH, CENTER)));
        repo.add(room(EAST, Map.of(Direction.WEST, CENTER)));
        repo.add(room(WEST, Map.of(Direction.EAST, CENTER)));
        repo.add(room(FAR_NORTH, Map.of(Direction.SOUTH, NORTH)));
        return repo;
    }

    private static Player playerVisiting(RoomId... visited) {
        User user = User.of(Username.of("cartographer"), Password.hash("pw", 1000));
        Player player = Player.of(user, "%hp> ");
        for (RoomId roomId : visited) {
            player = player.exploreRoom(roomId);
        }
        return player;
    }

    private static long countChar(String text, char symbol) {
        return text.chars().filter(c -> c == symbol).count();
    }

    /**
     * Returns only the drawable grid rows of a rendered map, excluding the header and the legend
     * line (the legend intentionally contains the {@code @}/{@code #}/{@code .} symbols).
     */
    private static String gridBody(String map) {
        String[] lines = map.split("\n", -1);
        StringBuilder body = new StringBuilder();
        for (int i = 1; i < lines.length - 1; i++) {
            body.append(lines[i]).append('\n');
        }
        return body.toString();
    }

    @Test
    void currentRoomIsAlwaysDrawnAsPlayerSymbol() {
        MapService service = new MapService(crossWorld());
        Player player = playerVisiting();

        String map = service.renderMap(player, CENTER);

        assertEquals(1, countChar(gridBody(map),'@'), "current room should be the single @");
    }

    @Test
    void unexploredExitsShowAsFrontierAndNotAsExplored() {
        MapService service = new MapService(crossWorld());
        Player player = playerVisiting();

        String map = service.renderMap(player, CENTER);

        // All four cardinal exits are unexplored, so they render as frontier dots.
        assertEquals(4, countChar(gridBody(map),'.'), "each unexplored exit renders as a dot");
        assertEquals(0, countChar(gridBody(map),'#'), "no rooms explored yet");
    }

    @Test
    void visitedRoomsRenderAsExplored() {
        MapService service = new MapService(crossWorld());
        Player player = playerVisiting(NORTH, EAST);

        String map = service.renderMap(player, CENTER);

        assertEquals(2, countChar(gridBody(map),'#'), "north and east are explored");
        // Frontier dots: center's south and west exits, plus far-north exposed behind explored north.
        assertEquals(3, countChar(gridBody(map),'.'), "unexplored exits render as frontier dots");
    }

    @Test
    void explorationOnlyExpandsThroughVisitedRooms() {
        MapService service = new MapService(crossWorld());
        // FAR_NORTH is visited but reachable only through NORTH, which is NOT visited.
        Player player = playerVisiting(FAR_NORTH);

        String map = service.renderMap(player, CENTER);

        // NORTH is an unexplored frontier from center; because we do not expand through it,
        // FAR_NORTH (behind it) is not drawn even though it is explored.
        assertEquals(0, countChar(gridBody(map),'#'), "explored room behind an unexplored room is hidden");
    }

    @Test
    void expandsThroughChainOfExploredRooms() {
        MapService service = new MapService(crossWorld());
        Player player = playerVisiting(NORTH, FAR_NORTH);

        String map = service.renderMap(player, CENTER);

        // NORTH and FAR_NORTH are both explored and connected; both should be drawn.
        assertEquals(2, countChar(gridBody(map),'#'), "chain of explored rooms is fully drawn");
    }

    @Test
    void frontierPositionsMatchExitDirections() {
        MapService service = new MapService(crossWorld());
        Player player = playerVisiting(NORTH);

        String[] lines = service.renderMap(player, CENTER).split("\n", -1);
        int playerLine = -1;
        int playerCol = -1;
        // Scan only the grid rows (skip the header at index 0 and the legend at the end).
        for (int i = 1; i < lines.length - 1; i++) {
            int col = lines[i].indexOf('@');
            if (col >= 0) {
                playerLine = i;
                playerCol = col;
            }
        }
        assertTrue(playerLine > 0, "player row should not be the first (header) line");
        // NORTH is explored and should sit directly above the player at the same column.
        assertEquals('#', lines[playerLine - 1].charAt(playerCol), "explored north is above @");
    }

    @Test
    void containsLegendAndHeader() {
        MapService service = new MapService(crossWorld());

        String map = service.renderMap(playerVisiting(), CENTER);

        assertTrue(map.startsWith("Map of your surroundings:"), "map has a header");
        assertTrue(map.contains("Legend:"), "map has a legend");
    }

    @Test
    void missingCurrentRoomStillRendersPlayer() {
        MapService service = new MapService(new MapRoomRepository());
        Player player = playerVisiting();

        String map = service.renderMap(player, RoomId.of("nowhere"));

        assertEquals(1, countChar(gridBody(map),'@'), "player is drawn even when the room is unknown");
        assertFalse(gridBody(map).contains("#"), "no neighbours when the room graph is empty");
    }
}
