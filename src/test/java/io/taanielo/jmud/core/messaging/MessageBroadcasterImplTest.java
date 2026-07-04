package io.taanielo.jmud.core.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
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
 * Unit tests for {@link MessageBroadcasterImpl} covering player, room, and global scoping,
 * plus exclusion sets and the offline-recipient no-op case.
 */
class MessageBroadcasterImplTest {

    private static final RoomId ROOM_ONE = RoomId.of("room-one");
    private static final RoomId ROOM_TWO = RoomId.of("room-two");

    @Test
    void sendToPlayerDeliversOnlyToTargetClient() {
        FakeClient alice = fakeClient("Alice");
        FakeClient bob = fakeClient("Bob");
        MessageBroadcaster broadcaster = broadcaster(List.of(alice, bob), twoRoomService());

        Message message = new PlainTextMessage("hello");
        broadcaster.sendToPlayer(Username.of("Bob"), message);

        assertTrue(alice.received.isEmpty(), "Only the target should receive the message");
        assertEquals(List.of(message), bob.received);
    }

    @Test
    void sendToPlayerToOfflinePlayerIsCleanNoOp() {
        FakeClient alice = fakeClient("Alice");
        MessageBroadcaster broadcaster = broadcaster(List.of(alice), twoRoomService());

        broadcaster.sendToPlayer(Username.of("Ghost"), new PlainTextMessage("hello?"));

        assertTrue(alice.received.isEmpty(), "No connected client should receive anything");
    }

    @Test
    void broadcastToRoomReachesOnlyRoomOccupants() {
        RoomService roomService = twoRoomService();
        FakeClient alice = fakeClient("Alice");
        FakeClient bob = fakeClient("Bob");
        FakeClient carol = fakeClient("Carol");
        roomService.ensurePlayerLocation(alice.player.getUsername());
        roomService.ensurePlayerLocation(bob.player.getUsername());
        roomService.ensurePlayerLocation(carol.player.getUsername());
        roomService.move(carol.player.getUsername(), Direction.NORTH);
        MessageBroadcaster broadcaster = broadcaster(List.of(alice, bob, carol), roomService);

        Message message = new PlainTextMessage("Alice says hi");
        broadcaster.broadcastToRoom(ROOM_ONE, message, Set.of());

        assertEquals(List.of(message), alice.received);
        assertEquals(List.of(message), bob.received);
        assertTrue(carol.received.isEmpty(), "Occupant of a different room must not receive the message");
    }

    @Test
    void broadcastToRoomHonoursExclusions() {
        RoomService roomService = twoRoomService();
        FakeClient alice = fakeClient("Alice");
        FakeClient bob = fakeClient("Bob");
        roomService.ensurePlayerLocation(alice.player.getUsername());
        roomService.ensurePlayerLocation(bob.player.getUsername());
        MessageBroadcaster broadcaster = broadcaster(List.of(alice, bob), roomService);

        Message message = new PlainTextMessage("Alice says hi");
        broadcaster.broadcastToRoom(ROOM_ONE, message, Set.of(Username.of("Alice")));

        assertTrue(alice.received.isEmpty(), "Excluded speaker must not receive the room broadcast");
        assertEquals(List.of(message), bob.received);
    }

    @Test
    void broadcastGlobalReachesEveryoneMinusExclusions() {
        FakeClient alice = fakeClient("Alice");
        FakeClient bob = fakeClient("Bob");
        FakeClient carol = fakeClient("Carol");
        MessageBroadcaster broadcaster = broadcaster(List.of(alice, bob, carol), twoRoomService());

        Message message = new PlainTextMessage("Alice gossips: hi everyone");
        broadcaster.broadcastGlobal(message, Set.of(Username.of("Alice")));

        assertTrue(alice.received.isEmpty(), "Sender should be excluded from the global broadcast");
        assertEquals(List.of(message), bob.received);
        assertEquals(List.of(message), carol.received);
    }

    // --- helpers ---

    private static MessageBroadcaster broadcaster(List<Client> clients, RoomService roomService) {
        ClientPool pool = new FakeClientPool(clients);
        return new MessageBroadcasterImpl(pool, roomService);
    }

    /** Two adjacent rooms (ROOM_ONE --north--> ROOM_TWO), starting room is ROOM_ONE. */
    private static RoomService twoRoomService() {
        Room roomOne = new Room(
            ROOM_ONE,
            "Room One",
            "The first room.",
            Map.of(Direction.NORTH, ROOM_TWO),
            List.of(),
            List.of()
        );
        Room roomTwo = new Room(
            ROOM_TWO,
            "Room Two",
            "The second room.",
            Map.of(Direction.SOUTH, ROOM_ONE),
            List.of(),
            List.of()
        );
        RoomRepository repository = new InMemoryRoomRepository(Map.of(ROOM_ONE, roomOne, ROOM_TWO, roomTwo));
        return new RoomService(repository, ROOM_ONE);
    }

    private static FakeClient fakeClient(String username) {
        User user = User.of(Username.of(username), Password.hash("secret"));
        Player player = Player.of(user, "%h/%H hp>");
        return new FakeClient(player);
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
        public int getNextId() {
            return clients.size();
        }

        @Override
        public List<Client> clients() {
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
}
