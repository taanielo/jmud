package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.action.GameActionResult;
import io.taanielo.jmud.core.action.GameMessage;
import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.world.AffixId;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Rarity;
import io.taanielo.jmud.core.world.RarityProfile;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * End-to-end style test that drives a real {@link MobRegistry} plus {@link WorldEventScheduler}: the
 * scheduler opens an event, spawning the rare-elite mob into the world, and a player kill routes
 * through the ordinary world-boss kill path — awarding loot, XP and gold and announcing the death
 * server-wide — while the event closes cleanly with no lingering respawn.
 */
class WorldEventKillTest {

    private static final RoomId ROOM_ID = RoomId.of("frozen-peaks-glacier");
    private static final AttackId UNARMED = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition BIG_HIT =
        new AttackDefinition(UNARMED, "punch", 50, 50, 0, 0, 0, List.of());

    private static final Item EPIC_REWARD = Item.builder(
        ItemId.of("rimewrought-heart"), "the Rimewrought Heart", "A heart of black ice.", ItemAttributes.empty())
        .rarity(RarityProfile.of(Rarity.EPIC, List.of(AffixId.of("of-the-titan"))))
        .build();

    @Test
    void scheduledEventIsKillableAndAwardsRewards() {
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        Player attacker = player("Grimtooth");

        AttackRepository attackRepo = new StubAttackRepository(Map.of(UNARMED, BIG_HIT));
        ItemRepository itemRepo = new StubItemRepository(Map.of(EPIC_REWARD.getId(), EPIC_REWARD));
        RoomService roomService = new RoomService(new StubRoomRepository(), ROOM_ID);
        roomService.ensurePlayerLocation(attacker.getUsername());
        PlayerRepository playerRepo = new StubPlayerRepository(attacker);
        PlayerEventBus bus = new PlayerEventBus();

        MobRegistry registry = new MobRegistry(
            new StubMobTemplateRepository(List.of(eventTemplate())), itemRepo, attackRepo, roomService,
            playerRepo, MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus, new DropEverythingRandom());
        WorldBossAnnouncer announcer = new WorldBossAnnouncer(broadcaster, roomService, null, null);
        registry.setWorldBossAnnouncer(announcer);
        registry.init();

        // No world-event mob is placed at start-up: it only appears via the scheduler.
        assertTrue(registry.getMobsInRoom(ROOM_ID).isEmpty(), "world-event mob must not spawn at init");

        WorldEventScheduler scheduler = new WorldEventScheduler(
            registry, announcer, new MinRandom(), 1, 1, 100);
        scheduler.tick(); // opens the event, spawning the mob

        assertTrue(scheduler.isEventActive(), "the event should be open after the scheduler fires");
        assertEquals(1, registry.getMobsInRoom(ROOM_ID).size(), "the elite should now be in the room");
        assertTrue(broadcaster.contains("tears open"), "spawn should have been announced");
        broadcaster.globals.clear();

        GameActionResult result = registry.processPlayerAttack(attacker, "Rimewrought", ROOM_ID);
        List<String> texts = result.messages().stream().map(GameMessage::text).toList();

        assertTrue(texts.stream().anyMatch(t -> t.contains("You slay")),
            "kill should slay the mob, got: " + texts);
        assertTrue(texts.stream().anyMatch(t -> t.contains("experience points")),
            "kill should award XP, got: " + texts);
        assertTrue(texts.stream().anyMatch(t -> t.contains("gold")),
            "kill should award gold, got: " + texts);
        assertTrue(texts.stream().anyMatch(t -> t.contains("Rimewrought Heart")),
            "the guaranteed epic reward should drop, got: " + texts);
        assertTrue(broadcaster.contains("has fallen to Grimtooth"),
            "the kill should be announced server-wide, got: " + broadcaster.globals);

        // The scheduler observes the kill and closes the event with no respawn scheduled.
        scheduler.tick();
        assertFalse(scheduler.isEventActive(), "the event should close once the mob is slain");
        assertTrue(registry.getMobsInRoom(ROOM_ID).isEmpty(), "the slain mob should be removed, not respawned");
    }

    // ── helpers ───────────────────────────────────────────────────────

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private static MobTemplate eventTemplate() {
        return new MobTemplate(
            MobId.of("rimewrought-stalker"),
            "the Rimewrought Stalker",
            5,
            AttackId.of("attack.rimewrought-stalker"),
            null,
            true,
            List.of(new LootEntry(EPIC_REWARD.getId(), 1.0)),
            ROOM_ID,
            1,
            0,
            320,
            new GoldDrop(80, 160),
            List.of("world-event"),
            false,
            null,
            null,
            false,
            null,
            null,
            true,   // worldBoss
            true    // worldEvent
        );
    }

    /** Drops every chance-based roll (nextDouble below any drop chance) and picks the minimum. */
    private static final class DropEverythingRandom implements CombatRandom {
        @Override
        public int roll(int minInclusive, int maxInclusive) {
            return minInclusive;
        }

        @Override
        public double nextDouble() {
            return 0.0d;
        }
    }

    /** Always returns the minimum of a range, so the scheduler's interval is exactly its min. */
    private static final class MinRandom implements CombatRandom {
        @Override
        public int roll(int minInclusive, int maxInclusive) {
            return minInclusive;
        }
    }

    private static final class CapturingBroadcaster implements MessageBroadcaster {
        private final List<Message> globals = new ArrayList<>();

        @Override
        public void sendToPlayer(Username target, Message message) {
        }

        @Override
        public void broadcastToRoom(RoomId room, Message message, Set<Username> exclude) {
        }

        @Override
        public void broadcastGlobal(Message message, Set<Username> exclude) {
            globals.add(message);
        }

        boolean contains(String fragment) {
            return globals.stream()
                .map(m -> ((PlainTextMessage) m).text())
                .anyMatch(t -> t.contains(fragment));
        }
    }

    private record StubMobTemplateRepository(List<MobTemplate> templates) implements MobTemplateRepository {
        @Override
        public List<MobTemplate> findAll() {
            return templates;
        }
    }

    private record StubAttackRepository(Map<AttackId, AttackDefinition> attacks) implements AttackRepository {
        @Override
        public Optional<AttackDefinition> findById(AttackId id) throws RepositoryException {
            return Optional.ofNullable(attacks.get(id));
        }
    }

    private static final class StubItemRepository implements ItemRepository {
        private final Map<ItemId, Item> items;

        StubItemRepository(Map<ItemId, Item> items) {
            this.items = Map.copyOf(items);
        }

        @Override
        public void save(Item item) throws RepositoryException {
        }

        @Override
        public Optional<Item> findById(ItemId id) throws RepositoryException {
            return Optional.ofNullable(items.get(id));
        }
    }

    private static final class StubPlayerRepository implements PlayerRepository {
        private final ConcurrentHashMap<Username, Player> store = new ConcurrentHashMap<>();

        StubPlayerRepository(Player initial) {
            store.put(initial.getUsername(), initial);
        }

        @Override
        public void savePlayer(Player player) {
            store.put(player.getUsername(), player);
        }

        @Override
        public Optional<Player> loadPlayer(Username username) {
            return Optional.ofNullable(store.get(username));
        }
    }

    private static final class StubRoomRepository implements RoomRepository {
        private final Room room =
            new Room(ROOM_ID, "Frozen Peaks Glacier", "A cracked blue glacier.", Map.of(), List.of(), List.of());

        @Override
        public void save(Room room) throws RepositoryException {
        }

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return room.getId().equals(id) ? Optional.of(room) : Optional.empty();
        }
    }
}
