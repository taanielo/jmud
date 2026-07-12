package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.MessageBroadcasterImpl;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Unit tests for {@link ShoutCommand}, exercising real {@link RoomService} and
 * {@link MessageBroadcaster} wiring so room-adjacency fan-out is verified end to end.
 */
class ShoutCommandTest {

    private static final RoomId ROOM_ONE = RoomId.of("room-one");
    private static final RoomId ROOM_TWO = RoomId.of("room-two");
    private static final RoomId ROOM_THREE = RoomId.of("room-three");
    private static final RoomId ROOM_FAR = RoomId.of("room-far");

    // --- token matching ---

    @Test
    void matchesShoutToken() {
        RoomService roomService = threeRoomService();
        MessageBroadcaster broadcaster = new MessageBroadcasterImpl(new FakeClientPool(List.of()), roomService);
        ShoutCommand cmd = new ShoutCommand(new SocketCommandRegistry(), roomService, broadcaster);
        assertTrue(cmd.match("SHOUT hello").isPresent());
        assertTrue(cmd.match("shout hello").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        RoomService roomService = threeRoomService();
        MessageBroadcaster broadcaster = new MessageBroadcasterImpl(new FakeClientPool(List.of()), roomService);
        ShoutCommand cmd = new ShoutCommand(new SocketCommandRegistry(), roomService, broadcaster);
        assertFalse(cmd.match("SAY hello").isPresent());
        assertFalse(cmd.match("").isPresent());
    }

    // --- delivery ---

    @Test
    void deliversToOwnRoomAndAdjacentRooms() {
        RoomService roomService = threeRoomService();
        FakeClient speaker = fakeClient("Speaker");
        FakeClient roommate = fakeClient("Roommate");
        FakeClient neighbor = fakeClient("Neighbor");
        FakeClient distant = fakeClient("Distant");
        roomService.ensurePlayerLocation(speaker.player.getUsername());
        roomService.ensurePlayerLocation(roommate.player.getUsername());
        roomService.ensurePlayerLocation(neighbor.player.getUsername());
        roomService.move(neighbor.player.getUsername(), Direction.NORTH);
        roomService.ensurePlayerLocation(distant.player.getUsername());
        roomService.move(distant.player.getUsername(), Direction.EAST);
        roomService.move(distant.player.getUsername(), Direction.NORTH);

        MessageBroadcaster broadcaster =
            new MessageBroadcasterImpl(new FakeClientPool(List.of(speaker, roommate, neighbor, distant)), roomService);
        ShoutCommand cmd = new ShoutCommand(new SocketCommandRegistry(), roomService, broadcaster);

        CapturingContext context = new CapturingContext(speaker.player);
        cmd.match("SHOUT hello everyone").get().execute(context);

        assertTrue(speaker.received.isEmpty(), "Speaker must not receive their own broadcast");
        assertEquals(1, roommate.received.size());
        assertEquals("Speaker shouts \"hello everyone\"", textOf(roommate.received.get(0)));
        assertEquals(1, neighbor.received.size());
        assertEquals("You hear Speaker shout \"hello everyone\" from nearby.", textOf(neighbor.received.get(0)));
        assertTrue(distant.received.isEmpty(), "A room two hops away must not receive the shout");
    }

    @Test
    void senderSeesConfirmation() {
        RoomService roomService = threeRoomService();
        FakeClient speaker = fakeClient("Speaker");
        roomService.ensurePlayerLocation(speaker.player.getUsername());
        MessageBroadcaster broadcaster =
            new MessageBroadcasterImpl(new FakeClientPool(List.of(speaker)), roomService);
        ShoutCommand cmd = new ShoutCommand(new SocketCommandRegistry(), roomService, broadcaster);

        CapturingContext context = new CapturingContext(speaker.player);
        cmd.match("SHOUT hello").get().execute(context);

        assertTrue(context.lines.contains("You shout \"hello\""));
    }

    @Test
    void missingMessageReturnsError() {
        RoomService roomService = threeRoomService();
        MessageBroadcaster broadcaster = new MessageBroadcasterImpl(new FakeClientPool(List.of()), roomService);
        ShoutCommand cmd = new ShoutCommand(new SocketCommandRegistry(), roomService, broadcaster);

        CapturingContext context = new CapturingContext(stubPlayer("Speaker"));
        cmd.match("SHOUT").get().execute(context);

        assertTrue(context.promptMessage.contains("Shout what"));
    }

    @Test
    void unauthenticatedUseIsRejected() {
        RoomService roomService = threeRoomService();
        MessageBroadcaster broadcaster = new MessageBroadcasterImpl(new FakeClientPool(List.of()), roomService);
        ShoutCommand cmd = new ShoutCommand(new SocketCommandRegistry(), roomService, broadcaster);

        CapturingContext context = new CapturingContext(null);
        cmd.match("SHOUT hello").get().execute(context);

        assertTrue(context.promptMessage.contains("logged in"));
    }

    // --- helpers ---

    private static String textOf(Message message) {
        StringBuilder captured = new StringBuilder();
        try {
            message.send(captured::append);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        return captured.toString().replace("\r\n", "");
    }

    /** Room one has two adjacent rooms (two/north, three/east) plus a far room two hops away. */
    private static RoomService threeRoomService() {
        Room roomOne = new Room(
            ROOM_ONE,
            "Room One",
            "The first room.",
            Map.of(Direction.NORTH, ROOM_TWO, Direction.EAST, ROOM_THREE),
            List.of(),
            List.of()
        );
        Room roomTwo = new Room(
            ROOM_TWO, "Room Two", "The second room.", Map.of(Direction.SOUTH, ROOM_ONE), List.of(), List.of());
        Room roomThree = new Room(
            ROOM_THREE,
            "Room Three",
            "The third room.",
            Map.of(Direction.WEST, ROOM_ONE, Direction.NORTH, ROOM_FAR),
            List.of(),
            List.of()
        );
        Room roomFar = new Room(
            ROOM_FAR, "Room Far", "A distant room.", Map.of(Direction.SOUTH, ROOM_THREE), List.of(), List.of());
        RoomRepository repository = new InMemoryRoomRepository(
            Map.of(ROOM_ONE, roomOne, ROOM_TWO, roomTwo, ROOM_THREE, roomThree, ROOM_FAR, roomFar));
        return new RoomService(repository, ROOM_ONE);
    }

    private static FakeClient fakeClient(String username) {
        return new FakeClient(stubPlayer(username));
    }

    private static Player stubPlayer(String name) {
        User user = User.of(Username.of(name), Password.hash("secret"));
        return Player.of(user, "%h/%H hp>");
    }

    private static final class FakeClient implements Client {
        private final Player player;
        private final List<Message> received = new ArrayList<>();

        private FakeClient(Player player) {
            this.player = player;
        }

        @Override
        public void sendMessage(Message message) {
            received.add(message);
        }

        @Override
        public void close() {
        }

        @Override
        public Optional<Player> currentPlayer() {
            return Optional.of(player);
        }

        @Override
        public void run() {
        }
    }

    private static final class FakeClientPool implements ClientPool {
        private final List<Client> clients;

        private FakeClientPool(List<Client> clients) {
            this.clients = new CopyOnWriteArrayList<>(clients);
        }

        @Override
        public void add(Client client) {
            clients.add(client);
        }

        @Override
        public void remove(Client client) {
            clients.remove(client);
        }

        @Override
        public void promoteToWorld(Client client) {
        }

        @Override
        public int getNextId() {
            return clients.size();
        }

        @Override
        public List<Client> allConnections() {
            return List.copyOf(clients);
        }

        // Every fixture client is treated as already in-world.
        @Override
        public List<Client> inWorld() {
            return List.copyOf(clients);
        }
    }

    private static final class InMemoryRoomRepository implements RoomRepository {
        private final Map<RoomId, Room> rooms;

        private InMemoryRoomRepository(Map<RoomId, Room> rooms) {
            this.rooms = rooms;
        }

        @Override
        public void save(Room room) throws RepositoryException {
            // not needed for these tests
        }

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return Optional.ofNullable(rooms.get(id));
        }
    }

    private static final class CapturingContext implements SocketCommandContext {
        final List<String> lines = new ArrayList<>();
        String promptMessage = "";
        final Map<String, String> sentToUsername = new HashMap<>();
        private final Player player;

        CapturingContext(Player player) {
            this.player = player;
        }

        @Override public boolean isAuthenticated() { return player != null; }
        @Override public Player getPlayer() { return player; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void writeLineWithPrompt(String m) { promptMessage = m; }
        @Override public void writeLineSafe(String m) { lines.add(m); }
        @Override public void sendPrompt() {}
        @Override public void sendToUsername(Username u, String m) { sentToUsername.put(u.getValue().toLowerCase(Locale.ROOT), m); }
        @Override public void sendToRoom(Player s, Player t, String m) {}
        @Override public void sendToRoom(Player s, String m) {}
        @Override public Optional<Player> resolveTarget(Player s, String i) { return Optional.empty(); }
        @Override public void executeAttack(String a) {}
        @Override public void getItem(String a) {}
        @Override public void dropItem(String a) {}
        @Override public void quaffItem(String a) {}
        @Override public void readItem(String a) {}
        @Override public void writeItem(String a) {}
        @Override public void equipItem(String a) {}
        @Override public void unequipItem(String a) {}
        @Override public void killMob(String a) {}
        @Override public void fleeCombat() {}
        @Override public void sendInventory() {}
        @Override public void sendEquipment() {}
        @Override public void sendMessage(Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}
