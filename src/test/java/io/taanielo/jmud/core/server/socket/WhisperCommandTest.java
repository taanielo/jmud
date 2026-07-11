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

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Unit tests for {@link WhisperCommand}.
 */
class WhisperCommandTest {

    private static final RoomId ROOM_ONE = RoomId.of("room-one");
    private static final RoomId ROOM_TWO = RoomId.of("room-two");

    // --- token matching ---

    @Test
    void matchesWhisperToken() {
        WhisperCommand cmd = new WhisperCommand(new SocketCommandRegistry(), twoRoomService());
        assertTrue(cmd.match("WHISPER taaniel hello").isPresent());
        assertTrue(cmd.match("whisper taaniel hello").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        WhisperCommand cmd = new WhisperCommand(new SocketCommandRegistry(), twoRoomService());
        assertFalse(cmd.match("TELL hello").isPresent());
        assertFalse(cmd.match("WHO").isPresent());
        assertFalse(cmd.match("").isPresent());
    }

    // --- delivery ---

    @Test
    void deliversWhisperToRecipientInSameRoom() {
        RoomService roomService = twoRoomService();
        CapturingContext context = new CapturingContext("Sender");
        context.addOnlinePlayer("Recipient");
        roomService.ensurePlayerLocation(Username.of("Sender"));
        roomService.ensurePlayerLocation(Username.of("Recipient"));

        WhisperCommand cmd = new WhisperCommand(new SocketCommandRegistry(), roomService);
        cmd.match("WHISPER Recipient hello there").get().execute(context);

        assertEquals("Sender whispers to you: hello there",
                context.sentToUsername.get("recipient"),
                "Recipient should receive the formatted whisper message");
    }

    @Test
    void senderSeesConfirmation() {
        RoomService roomService = twoRoomService();
        CapturingContext context = new CapturingContext("Sender");
        context.addOnlinePlayer("Recipient");
        roomService.ensurePlayerLocation(Username.of("Sender"));
        roomService.ensurePlayerLocation(Username.of("Recipient"));

        WhisperCommand cmd = new WhisperCommand(new SocketCommandRegistry(), roomService);
        cmd.match("WHISPER Recipient hello there").get().execute(context);

        assertTrue(context.lines.stream().anyMatch(l -> l.equals("You whisper to Recipient: hello there")),
                "Sender should see confirmation line");
    }

    @Test
    void targetNotOnlineReturnsError() {
        RoomService roomService = twoRoomService();
        CapturingContext context = new CapturingContext("Sender");
        roomService.ensurePlayerLocation(Username.of("Sender"));
        // No players added — target is offline.

        WhisperCommand cmd = new WhisperCommand(new SocketCommandRegistry(), roomService);
        cmd.match("WHISPER Ghost hello").get().execute(context);

        assertTrue(context.promptMessage.contains("Ghost") && context.promptMessage.contains("not here"),
                "Error message should name the missing player");
    }

    @Test
    void targetInDifferentRoomReturnsError() {
        RoomService roomService = twoRoomService();
        CapturingContext context = new CapturingContext("Sender");
        context.addOnlinePlayer("Recipient");
        roomService.ensurePlayerLocation(Username.of("Sender"));
        roomService.ensurePlayerLocation(Username.of("Recipient"));
        roomService.move(Username.of("Recipient"), Direction.NORTH);

        WhisperCommand cmd = new WhisperCommand(new SocketCommandRegistry(), roomService);
        cmd.match("WHISPER Recipient hello").get().execute(context);

        assertTrue(context.promptMessage.contains("Recipient") && context.promptMessage.contains("not here"),
                "Error message should indicate the target is not in this room");
        assertTrue(context.sentToUsername.isEmpty(), "Message must not be delivered across rooms");
    }

    @Test
    void missingMessageReturnsUsageError() {
        RoomService roomService = twoRoomService();
        CapturingContext context = new CapturingContext("Sender");
        context.addOnlinePlayer("Recipient");

        WhisperCommand cmd = new WhisperCommand(new SocketCommandRegistry(), roomService);
        cmd.match("WHISPER Recipient").get().execute(context);

        assertFalse(context.promptMessage.isBlank(), "Usage error should be written via writeLineWithPrompt");
        assertTrue(context.promptMessage.contains("Usage"), "Error should mention usage");
    }

    @Test
    void missingAllArgsReturnsUsageError() {
        RoomService roomService = twoRoomService();
        CapturingContext context = new CapturingContext("Sender");

        WhisperCommand cmd = new WhisperCommand(new SocketCommandRegistry(), roomService);
        cmd.match("WHISPER").get().execute(context);

        assertFalse(context.promptMessage.isBlank(), "Usage error should be written via writeLineWithPrompt");
        assertTrue(context.promptMessage.contains("Usage"), "Error should mention usage");
    }

    @Test
    void unauthenticatedUseIsRejected() {
        RoomService roomService = twoRoomService();
        CapturingContext context = new CapturingContext(null);

        WhisperCommand cmd = new WhisperCommand(new SocketCommandRegistry(), roomService);
        cmd.match("WHISPER Recipient hello").get().execute(context);

        assertTrue(context.promptMessage.contains("logged in"));
    }

    // --- helpers ---

    /** Two adjacent rooms (ROOM_ONE --north--> ROOM_TWO), starting room is ROOM_ONE. */
    private static RoomService twoRoomService() {
        Room roomOne = new Room(
            ROOM_ONE, "Room One", "The first room.", Map.of(Direction.NORTH, ROOM_TWO), List.of(), List.of());
        Room roomTwo = new Room(
            ROOM_TWO, "Room Two", "The second room.", Map.of(Direction.SOUTH, ROOM_ONE), List.of(), List.of());
        RoomRepository repository = new InMemoryRoomRepository(Map.of(ROOM_ONE, roomOne, ROOM_TWO, roomTwo));
        return new RoomService(repository, ROOM_ONE);
    }

    private static Player stubPlayer(String name) {
        User user = User.of(Username.of(name), Password.hash("secret"));
        return Player.of(user, "%h/%H hp>");
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
        private final List<Username> onlinePlayers = new ArrayList<>();
        private final Player player;

        CapturingContext(String senderName) {
            this.player = senderName == null ? null : stubPlayer(senderName);
        }

        void addOnlinePlayer(String name) {
            onlinePlayers.add(Username.of(name));
        }

        @Override public boolean isAuthenticated() { return player != null; }
        @Override public Player getPlayer() { return player; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.copyOf(onlinePlayers); }
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
        @Override public void sendMessage(io.taanielo.jmud.core.messaging.Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}
