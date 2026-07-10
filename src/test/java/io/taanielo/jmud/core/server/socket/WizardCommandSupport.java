package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.messaging.SystemNoticeMessage;
import io.taanielo.jmud.core.mob.MobRegistry;
import io.taanielo.jmud.core.mob.MobRegistryTestFactory;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.PlayerLocationService;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomItemService;
import io.taanielo.jmud.core.world.RoomRenderer;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Shared test helpers for the wizard/admin socket command tests (GOTO/SPAWN/PURGE/SHUTDOWN):
 * capturing doubles for the command context and message broadcaster, plus small builders for
 * players, wizard policies, rooms, and a live {@link MobRegistry}.
 */
final class WizardCommandSupport {

    private WizardCommandSupport() {
    }

    static Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("secret", 1));
        return Player.of(user, "%h/%H hp>");
    }

    static WizardPolicy wizardPolicy(String... names) {
        Set<Username> wizards = java.util.Arrays.stream(names).map(Username::of)
            .collect(Collectors.toUnmodifiableSet());
        return new WizardPolicy(wizards);
    }

    /** Renders the text carried by a captured message for assertions. */
    static String text(Message message) {
        return switch (message) {
            case PlainTextMessage p -> p.text();
            case SystemNoticeMessage s -> s.text();
            default -> message.toString();
        };
    }

    static RoomRepository roomRepository(Room... rooms) {
        Map<RoomId, Room> byId = java.util.Arrays.stream(rooms)
            .collect(Collectors.toMap(Room::getId, r -> r));
        return new StubRoomRepository(byId);
    }

    static Room room(RoomId id, String name) {
        return new Room(id, name, "A test room.", Map.of(), List.of(), List.of());
    }

    /** Builds a RoomService and its shared PlayerLocationService over the given repository. */
    static RoomWorld world(RoomId startRoom, Room... rooms) {
        RoomRepository repo = roomRepository(rooms);
        PlayerLocationService location = new PlayerLocationService(repo, startRoom);
        RoomService roomService = new RoomService(location, new RoomItemService(), new RoomRenderer(), repo);
        return new RoomWorld(location, roomService);
    }

    record RoomWorld(PlayerLocationService location, RoomService roomService) {
    }

    static MobRegistry mobRegistry(RoomService roomService, List<MobTemplate> templates) {
        return MobRegistryTestFactory.create(roomService, templates);
    }

    static final class CapturingBroadcaster implements MessageBroadcaster {
        record RoomDelivery(RoomId room, Message message, Set<Username> exclude) {
        }

        record GlobalDelivery(Message message, Set<Username> exclude) {
        }

        final List<RoomDelivery> roomDeliveries = new ArrayList<>();
        final List<GlobalDelivery> globalDeliveries = new ArrayList<>();
        final List<Message> playerDeliveries = new ArrayList<>();

        @Override
        public void sendToPlayer(Username target, Message message) {
            playerDeliveries.add(message);
        }

        @Override
        public void broadcastToRoom(RoomId room, Message message, Set<Username> exclude) {
            roomDeliveries.add(new RoomDelivery(room, message, exclude));
        }

        @Override
        public void broadcastGlobal(Message message, Set<Username> exclude) {
            globalDeliveries.add(new GlobalDelivery(message, exclude));
        }
    }

    /**
     * A {@link SocketCommandContext} double that records prompt/line output and exposes
     * configurable authentication, player identity, online-player set, and a look flag.
     */
    static final class CapturingContext implements SocketCommandContext {
        final List<String> lines = new ArrayList<>();
        String promptMessage = "";
        boolean lookSent = false;
        private final Player player;
        private final List<Username> onlineNames;

        CapturingContext(Player player) {
            this(player, List.of());
        }

        CapturingContext(Player player, List<Username> onlineNames) {
            this.player = player;
            this.onlineNames = List.copyOf(onlineNames);
        }

        @Override public boolean isAuthenticated() { return player != null; }
        @Override public Player getPlayer() { return player; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return onlineNames; }
        @Override public void sendLook() { lookSent = true; }
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void writeLineWithPrompt(String m) { promptMessage = m; }
        @Override public void writeLineSafe(String m) { lines.add(m); }
        @Override public void sendPrompt() {}
        @Override public void sendToUsername(Username u, String m) {}
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

    private record StubRoomRepository(Map<RoomId, Room> rooms) implements RoomRepository {
        @Override public void save(Room room) throws RepositoryException {}

        @Override public Optional<Room> findById(RoomId id) throws RepositoryException {
            return Optional.ofNullable(rooms.get(id));
        }
    }

    /** Simple in-memory player repository recording deletions for PURGE tests. */
    static final class RecordingPlayerRepository implements PlayerRepository {
        private final ConcurrentHashMap<Username, Player> store = new ConcurrentHashMap<>();
        final List<Username> deleted = new ArrayList<>();

        void put(Player player) { store.put(player.getUsername(), player); }

        @Override public void savePlayer(Player player) { store.put(player.getUsername(), player); }

        @Override public Optional<Player> loadPlayer(Username username) {
            return Optional.ofNullable(store.get(username));
        }

        @Override public boolean deletePlayer(Username username) {
            deleted.add(username);
            return store.remove(username) != null;
        }
    }
}
