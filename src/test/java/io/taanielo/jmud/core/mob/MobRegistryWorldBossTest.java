package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Tests the world-boss lifecycle handling in {@link MobRegistry}: spawn/respawn/death announcements
 * fire exactly once for a world boss, ordinary mobs stay silent, and a world-boss kill guarantees a
 * rare-or-higher loot drop.
 */
class MobRegistryWorldBossTest {

    private static final RoomId ROOM_ID = RoomId.of("frozen-peaks-summit");
    private static final AttackId DEFAULT_ATTACK = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition ONE_DMG_ATTACK =
        new AttackDefinition(DEFAULT_ATTACK, "punch", 1, 1, 0, 0, 0, List.of());

    private static final Item RARE_SWORD = Item.builder(
        ItemId.of("runed-longsword"), "a runed longsword", "A rune-etched blade.", ItemAttributes.empty())
        .rarity(RarityProfile.of(Rarity.RARE, List.of(AffixId.of("of-the-titan"))))
        .build();

    private static final Item COMMON_POTION = Item.builder(
        ItemId.of("health-potion"), "a health potion", "A red vial.", ItemAttributes.empty())
        .build();

    // ── tests ─────────────────────────────────────────────────────────

    @Test
    void init_announcesWorldBossSpawnExactlyOnce() {
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        buildRegistry(worldBoss(1, 300), broadcaster, player("hero"), new ArrayList<>());

        assertEquals(1, broadcaster.globals.size(), "Expected one spawn announcement on world load");
        assertTrue(broadcaster.text(0).contains("has awoken in"),
            "Expected an awakening announcement, got: " + broadcaster.text(0));
        assertTrue(broadcaster.text(0).contains("Vharixis the Frost Wyrm"),
            "Expected the boss name in the announcement, got: " + broadcaster.text(0));
    }

    @Test
    void init_ordinaryMobProducesNoAnnouncements() {
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        Player attacker = player("hero");
        MobRegistry registry = buildRegistry(ordinaryMob(1), broadcaster, attacker, new ArrayList<>());

        assertEquals(0, broadcaster.globals.size(), "Ordinary mobs must not broadcast on spawn");

        registry.processPlayerAttack(attacker, "Goblin", ROOM_ID);

        assertEquals(0, broadcaster.globals.size(), "Ordinary mob kills must not broadcast");
    }

    @Test
    void kill_announcesDeathExactlyOnce() {
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        Player attacker = player("Grimtooth");
        MobRegistry registry = buildRegistry(worldBoss(1, 300), broadcaster, attacker, new ArrayList<>());
        broadcaster.globals.clear(); // discard the spawn announcement

        registry.processPlayerAttack(attacker, "Vharixis", ROOM_ID);

        assertEquals(1, broadcaster.globals.size(), "Expected exactly one death announcement");
        assertTrue(broadcaster.text(0).contains("has fallen to Grimtooth"),
            "Expected the killer named in the death announcement, got: " + broadcaster.text(0));
    }

    @Test
    void kill_guaranteesRareLootDrop() {
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        Player attacker = player("hero");
        MobRegistry registry = buildRegistry(worldBoss(1, 300), broadcaster, attacker, new ArrayList<>());

        GameActionResult result = registry.processPlayerAttack(attacker, "Vharixis", ROOM_ID);

        List<String> texts = result.messages().stream().map(GameMessage::text).toList();
        assertTrue(
            texts.stream().anyMatch(t -> t.contains("glittering") && t.contains("runed longsword")),
            "Expected a guaranteed rare drop message, got: " + texts);
    }

    @Test
    void respawn_announcesWorldBossSpawnAgain() {
        CapturingBroadcaster broadcaster = new CapturingBroadcaster();
        Player attacker = player("hero");
        // respawnTicks == 1 so a single tick after death repopulates the boss.
        MobRegistry registry = buildRegistry(worldBoss(1, 1), broadcaster, attacker, new ArrayList<>());
        registry.processPlayerAttack(attacker, "Vharixis", ROOM_ID);
        broadcaster.globals.clear(); // discard spawn + death announcements

        registry.tick();

        assertEquals(1, broadcaster.globals.size(), "Expected a respawn spawn announcement");
        assertTrue(broadcaster.text(0).contains("has awoken in"),
            "Expected an awakening announcement on respawn, got: " + broadcaster.text(0));
    }

    // ── helpers ───────────────────────────────────────────────────────

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private MobTemplate worldBoss(int maxHp, int respawnTicks) {
        return new MobTemplate(
            MobId.of("frost-wyrm"),
            "Vharixis the Frost Wyrm",
            maxHp,
            DEFAULT_ATTACK,
            null,
            false,
            List.of(new LootEntry(RARE_SWORD.getId(), 0.5)),
            ROOM_ID,
            1,
            respawnTicks,
            5,
            null,
            List.of("boss"),
            false,
            null,
            null,
            false,
            null,
            null,
            true // worldBoss
        );
    }

    private MobTemplate ordinaryMob(int maxHp) {
        return new MobTemplate(
            MobId.of("goblin"),
            "Goblin",
            maxHp,
            DEFAULT_ATTACK,
            null,
            false,
            List.of(new LootEntry(COMMON_POTION.getId(), 0.5)),
            ROOM_ID,
            1,
            10,
            5,
            null,
            List.of(),
            false
        );
    }

    private MobRegistry buildRegistry(
        MobTemplate template,
        CapturingBroadcaster broadcaster,
        Player attacker,
        List<GameActionResult> captured
    ) {
        AttackRepository attackRepo = new StubAttackRepository(Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK));
        ItemRepository itemRepo = new StubItemRepository(
            Map.of(RARE_SWORD.getId(), RARE_SWORD, COMMON_POTION.getId(), COMMON_POTION));
        RoomService roomService = new RoomService(new StubRoomRepository(), ROOM_ID);
        roomService.ensurePlayerLocation(attacker.getUsername());
        PlayerRepository playerRepo = new StubPlayerRepository(attacker);
        PlayerEventBus bus = new PlayerEventBus();
        bus.register(attacker.getUsername(), captured::add);

        MobRegistry registry = new MobRegistry(
            new StubMobTemplateRepository(List.of(template)), itemRepo, attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus, new NoLootRandom());
        registry.setWorldBossAnnouncer(new WorldBossAnnouncer(broadcaster, roomService, null, null));
        registry.init();
        return registry;
    }

    // ── stubs ─────────────────────────────────────────────────────────

    /** Never triggers a chance-based loot roll (nextDouble above any drop chance) and picks index 0. */
    private static final class NoLootRandom implements CombatRandom {
        @Override
        public int roll(int minInclusive, int maxInclusive) {
            return minInclusive;
        }

        @Override
        public double nextDouble() {
            return 0.99d;
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

        String text(int index) {
            return ((PlainTextMessage) globals.get(index)).text();
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
            new Room(ROOM_ID, "Frozen Peaks Summit", "A frozen summit.", Map.of(), List.of(), List.of());

        @Override
        public void save(Room room) throws RepositoryException {
        }

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return room.getId().equals(id) ? Optional.of(room) : Optional.empty();
        }
    }
}
