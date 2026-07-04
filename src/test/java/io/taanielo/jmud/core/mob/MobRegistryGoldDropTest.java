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
 * Tests that gold is awarded to the killing player when a mob has a
 * {@link GoldDrop} configured, via both the instant-kill
 * ({@code processPlayerAttack}) and tick-based ({@code runPlayerCombat}) paths.
 */
class MobRegistryGoldDropTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId DEFAULT_ATTACK =
        AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition ONE_DMG_ATTACK =
        new AttackDefinition(DEFAULT_ATTACK, "punch", 1, 1, 0, 0, 0, List.of());

    // ── helpers ───────────────────────────────────────────────────────

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    /** Creates a mob that drops a fixed [5, 5] range of gold (always 5). */
    private MobTemplate templateWithFixedGoldDrop(int maxHp) {
        GoldDrop drop = new GoldDrop(5, 5);
        return new MobTemplate(
            MobId.of("mob.goblin"),
            "Goblin",
            maxHp,
            null,
            false,
            List.of(),
            ROOM_ID,
            1,
            10,
            5,
            drop,
            null,
            false
        );
    }

    /** Creates a mob with no gold drop. */
    private MobTemplate templateWithNoGoldDrop(int maxHp) {
        return new MobTemplate(
            MobId.of("mob.rat"),
            "Rat",
            maxHp,
            null,
            false,
            List.of(),
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
        Player attacker,
        StubPlayerRepository playerRepo,
        List<GameActionResult> captured
    ) {
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK));
        ItemRepository itemRepo = new StubItemRepository(Map.of());

        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(attacker.getUsername());

        PlayerEventBus bus = new PlayerEventBus();
        bus.register(attacker.getUsername(), captured::add);

        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo, bus);
        registry.init();
        return registry;
    }

    // ── tests ─────────────────────────────────────────────────────────

    /**
     * When a player kills a mob in one hit via {@code processPlayerAttack}
     * and the mob has a gold drop, the returned messages must contain a gold
     * announcement and the player's saved gold must increase.
     */
    @Test
    void processPlayerAttack_awardsGoldOnKill() {
        Player attacker = player("hero");
        MobTemplate template = templateWithFixedGoldDrop(1); // 1 HP → one-shot kill

        StubPlayerRepository playerRepo = new StubPlayerRepository(attacker);
        List<GameActionResult> captured = new ArrayList<>();
        MobRegistry registry = buildRegistry(template, attacker, playerRepo, captured);

        GameActionResult result = registry.processPlayerAttack(attacker, "Goblin", ROOM_ID);

        List<String> texts = result.messages().stream()
            .map(GameMessage::text)
            .toList();

        assertTrue(
            texts.stream().anyMatch(t -> t.contains("gold coin")),
            "Expected a gold drop message but got: " + texts);

        Player saved = playerRepo.load(attacker.getUsername());
        assertTrue(saved.getGold() > 0,
            "Expected saved player to have gold > 0, got " + saved.getGold());
        assertEquals(5, saved.getGold(), "Expected exactly 5 gold (fixed drop range 5-5)");
    }

    /**
     * When a mob with no gold drop is killed, no gold message is emitted and
     * the player's gold remains at 0.
     */
    @Test
    void processPlayerAttack_noGoldMessage_whenGoldDropAbsent() {
        Player attacker = player("hero");
        MobTemplate template = templateWithNoGoldDrop(1);

        StubPlayerRepository playerRepo = new StubPlayerRepository(attacker);
        List<GameActionResult> captured = new ArrayList<>();
        MobRegistry registry = buildRegistry(template, attacker, playerRepo, captured);

        GameActionResult result = registry.processPlayerAttack(attacker, "Rat", ROOM_ID);

        List<String> texts = result.messages().stream()
            .map(GameMessage::text)
            .toList();

        assertTrue(
            texts.stream().noneMatch(t -> t.contains("gold coin")),
            "Expected no gold message for mob with no gold drop, but got: " + texts);

        Player saved = playerRepo.load(attacker.getUsername());
        assertEquals(0, saved.getGold(), "Expected 0 gold for mob with no gold drop");
    }

    /**
     * When the killing blow lands during a combat tick ({@code runPlayerCombat}),
     * the gold award must be published to the player via the event bus and
     * the player's persisted gold must increase.
     */
    @Test
    void runPlayerCombat_awardsGoldOnKill() {
        Player attacker = player("hero");
        // 2 HP: first attack leaves 1 HP, second tick finishes the mob.
        MobTemplate template = templateWithFixedGoldDrop(2);

        StubPlayerRepository playerRepo = new StubPlayerRepository(attacker);
        List<GameActionResult> captured = new ArrayList<>();
        MobRegistry registry = buildRegistry(template, attacker, playerRepo, captured);

        // First hit — mob survives, combat is registered.
        registry.processPlayerAttack(attacker, "Goblin", ROOM_ID);
        captured.clear();

        // Tick — killing blow via runPlayerCombat.
        registry.tick();

        List<String> allTexts = captured.stream()
            .flatMap(r -> r.messages().stream())
            .map(GameMessage::text)
            .toList();

        assertTrue(
            allTexts.stream().anyMatch(t -> t.contains("gold coin")),
            "Expected gold drop message on tick kill, but got: " + allTexts);

        Player saved = playerRepo.load(attacker.getUsername());
        assertTrue(saved.getGold() > 0,
            "Expected saved player to have gold after tick kill, got " + saved.getGold());
    }

    // ── stubs ─────────────────────────────────────────────────────────

    private record StubMobTemplateRepository(List<MobTemplate> templates)
        implements MobTemplateRepository {
        @Override
        public List<MobTemplate> findAll() { return templates; }
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
        StubItemRepository(Map<ItemId, Item> items) { this.items = Map.copyOf(items); }
        @Override public void save(Item item) {}
        @Override
        public Optional<Item> findById(ItemId id) {
            return Optional.ofNullable(items.get(id));
        }
    }

    /** Mutable stub that allows inspecting what was saved. */
    static class StubPlayerRepository implements PlayerRepository {
        private final ConcurrentHashMap<Username, Player> store = new ConcurrentHashMap<>();
        StubPlayerRepository(Player initial) { store.put(initial.getUsername(), initial); }
        @Override public void savePlayer(Player player) { store.put(player.getUsername(), player); }
        @Override public Optional<Player> loadPlayer(Username username) {
            return Optional.ofNullable(store.get(username));
        }
        Player load(Username username) { return store.get(username); }
    }

    private static class StubRoomRepository implements RoomRepository {
        private final Room room;
        StubRoomRepository(RoomId roomId) {
            this.room = new Room(roomId, "Test Room", "A void.", Map.of(), List.of(), List.of());
        }
        @Override public void save(Room room) {}
        @Override public Optional<Room> findById(RoomId id) throws RepositoryException {
            return room.getId().equals(id) ? Optional.of(room) : Optional.empty();
        }
    }
}
