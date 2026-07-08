package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Tests that loot drop announcements are emitted to the killing player
 * via both the instant-kill ({@code processPlayerAttack}) and tick-based
 * ({@code runPlayerCombat}) kill paths.
 */
class MobRegistryLootAnnouncementTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId DEFAULT_ATTACK =
        AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);

    // Attack that always deals exactly 1 damage (min == max, no bonus)
    private static final AttackDefinition ONE_DMG_ATTACK =
        new AttackDefinition(DEFAULT_ATTACK, "punch", 1, 1, 0, 0, 0, List.of());

    private static final Item HEALTH_POTION = Item.builder(
        ItemId.of("item.health_potion"), "health potion", "A small red vial.", ItemAttributes.empty())
        .weight(1)
        .value(10)
        .build();

    // ── helpers ───────────────────────────────────────────────────────

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    /** Creates a MobTemplate whose loot table guarantees the given item drops. */
    private MobTemplate templateWithGuaranteedLoot(int maxHp, Item item) {
        LootEntry guaranteedDrop = new LootEntry(item.getId(), 1.0);
        return new MobTemplate(
            MobId.of("mob.goblin"),
            "Goblin",
            maxHp,
            null,   // attackId – mob won't attack in these tests
            null,
            false,
            List.of(guaranteedDrop),
            ROOM_ID,
            1,
            10,
            5,
            null,
            null,
            false
        );
    }

    private MobRegistry buildRegistry(
        MobTemplate template,
        Item item,
        Player attacker,
        List<GameActionResult> captured
    ) {
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(
            Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK));
        ItemRepository itemRepo = new StubItemRepository(
            Map.of(item.getId(), item));

        RoomService roomService = new RoomService(
            new StubRoomRepository(ROOM_ID),
            ROOM_ID
        );
        roomService.ensurePlayerLocation(attacker.getUsername());

        PlayerRepository playerRepo = new StubPlayerRepository(attacker);

        PlayerEventBus bus = new PlayerEventBus();
        bus.register(attacker.getUsername(), captured::add);

        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus, MobRegistryTestSupport.random());
        registry.init();
        return registry;
    }

    // ── tests ─────────────────────────────────────────────────────────

    /**
     * When a player's instant attack kills a mob that has a loot entry with
     * 100% drop chance, a drop announcement must appear in the returned messages.
     */
    @Test
    void processPlayerAttack_announcesLootDropOnKill() {
        Player attacker = player("hero");
        MobTemplate template = templateWithGuaranteedLoot(1, HEALTH_POTION);

        List<GameActionResult> captured = new ArrayList<>();
        MobRegistry registry = buildRegistry(template, HEALTH_POTION, attacker, captured);

        GameActionResult result = registry.processPlayerAttack(
            attacker, "Goblin", ROOM_ID);

        List<String> texts = result.messages().stream()
            .map(GameMessage::text)
            .toList();

        assertTrue(
            texts.stream().anyMatch(t -> t.contains("health potion")),
            "Expected a drop message for 'health potion' but got: " + texts);
        assertTrue(
            texts.stream().anyMatch(t -> t.contains("drops to the ground")),
            "Expected 'drops to the ground' in messages but got: " + texts);
    }

    /**
     * When the killing blow lands during a combat tick (runPlayerCombat path),
     * the drop announcement must be published to the player via the event bus.
     */
    @Test
    void runPlayerCombat_announcesLootDropOnKill() {
        Player attacker = player("hero");
        // Give the mob 2 HP: first attack (processPlayerAttack) leaves it at 1 HP,
        // second attack during the next tick finishes it.
        MobTemplate template = templateWithGuaranteedLoot(2, HEALTH_POTION);

        List<GameActionResult> captured = new ArrayList<>();
        MobRegistry registry = buildRegistry(template, HEALTH_POTION, attacker, captured);

        // First hit: mob survives, combat is registered.
        registry.processPlayerAttack(attacker, "Goblin", ROOM_ID);
        captured.clear();  // ignore the first-hit messages

        // Tick: runPlayerCombat fires, deals 1 more damage -> mob dies.
        registry.tick();

        List<String> allTexts = captured.stream()
            .flatMap(r -> r.messages().stream())
            .map(GameMessage::text)
            .toList();

        assertTrue(
            allTexts.stream().anyMatch(t -> t.contains("health potion")),
            "Expected a drop message for 'health potion' but got: " + allTexts);
        assertTrue(
            allTexts.stream().anyMatch(t -> t.contains("drops to the ground")),
            "Expected 'drops to the ground' in messages but got: " + allTexts);
    }

    /**
     * When a mob's loot table is empty, no drop messages are emitted.
     */
    @Test
    void processPlayerAttack_noDropMessages_whenLootTableEmpty() {
        Player attacker = player("hero");
        MobTemplate template = new MobTemplate(
            MobId.of("mob.goblin"),
            "Goblin",
            1,
            null,
            null,
            false,
            List.of(),   // empty loot table
            ROOM_ID,
            1,
            10,
            5,
            null,
            null,
            false
        );
        AttackRepository attackRepo = new StubAttackRepository(
            Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK));
        ItemRepository itemRepo = new StubItemRepository(Map.of());
        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(attacker.getUsername());
        PlayerRepository playerRepo = new StubPlayerRepository(attacker);
        PlayerEventBus bus = new PlayerEventBus();

        MobRegistry registry = new MobRegistry(
            new StubMobTemplateRepository(List.of(template)),
            itemRepo, attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus, MobRegistryTestSupport.random());
        registry.init();

        GameActionResult result = registry.processPlayerAttack(
            attacker, "Goblin", ROOM_ID);

        long dropMsgCount = result.messages().stream()
            .map(GameMessage::text)
            .filter(t -> t.contains("drops to the ground"))
            .count();

        assertEquals(0, dropMsgCount, "Expected no drop messages for empty loot table");
    }

    // ── stubs ─────────────────────────────────────────────────────────

    private record StubMobTemplateRepository(List<MobTemplate> templates)
        implements MobTemplateRepository {

        @Override
        public List<MobTemplate> findAll() {
            return templates;
        }
    }

    private record StubAttackRepository(Map<AttackId, AttackDefinition> attacks)
        implements AttackRepository {

        @Override
        public Optional<AttackDefinition> findById(AttackId id) throws RepositoryException {
            return Optional.ofNullable(attacks.get(id));
        }
    }

    private static class StubItemRepository implements ItemRepository {
        private final Map<ItemId, Item> items;

        StubItemRepository(Map<ItemId, Item> items) {
            this.items = Map.copyOf(items);
        }

        @Override
        public void save(Item item) throws RepositoryException {
            // no-op
        }

        @Override
        public Optional<Item> findById(ItemId id) throws RepositoryException {
            return Optional.ofNullable(items.get(id));
        }
    }

    private static class StubPlayerRepository implements PlayerRepository {
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

    private static class StubRoomRepository implements RoomRepository {
        private final Room room;

        StubRoomRepository(RoomId roomId) {
            this.room = new Room(
                roomId,
                "Test Room",
                "A featureless void.",
                Map.of(),
                List.of(),
                List.of()
            );
        }

        @Override
        public void save(Room room) throws RepositoryException {
            // no-op
        }

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return room.getId().equals(id) ? Optional.of(room) : Optional.empty();
        }
    }
}
