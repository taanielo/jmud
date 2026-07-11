package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.action.GameActionResult;
import io.taanielo.jmud.core.action.GameMessage;
import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.character.repository.RaceRepository;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.party.LootMode;
import io.taanielo.jmud.core.party.PartyService;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.EncumbranceService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Integration tests for solo autoloot on mob kills: items go straight into the killer's inventory
 * when autoloot is on and they have room, fall back to the floor when full or when autoloot is off,
 * and round-robin party loot takes precedence over the autoloot preference.
 */
class MobRegistrySoloAutoLootTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId DEFAULT_ATTACK = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition ONE_DMG_ATTACK =
        new AttackDefinition(DEFAULT_ATTACK, "punch", 1, 1, 0, 0, 0, List.of());

    private static final Item DAGGER = Item.builder(
        ItemId.of("item.rusty_dagger"), "rusty dagger", "A pitted blade.", ItemAttributes.empty())
        .weight(1)
        .value(5)
        .build();

    private final Map<Username, List<GameActionResult>> captured = new ConcurrentHashMap<>();
    private StubPlayerRepository playerRepo;
    private PersistenceQueue queue;
    private RoomService roomService;

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private MobTemplate goblin() {
        return new MobTemplate(
            MobId.of("mob.goblin"), "Goblin", 1, null, null, false,
            List.of(new LootEntry(DAGGER.getId(), 1.0)),
            ROOM_ID, 1, 10, 5, null, null, false);
    }

    /** Builds a registry with a single solo player (no party). */
    private MobRegistry buildSoloRegistry(Player attacker, EncumbranceService encumbrance) {
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(goblin()));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK));
        ItemRepository itemRepo = new StubItemRepository(Map.of(DAGGER.getId(), DAGGER));

        roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(attacker.getUsername());

        playerRepo = new StubPlayerRepository(List.of(attacker));

        PlayerEventBus bus = new PlayerEventBus();
        captured.put(attacker.getUsername(), new ArrayList<>());
        bus.register(attacker.getUsername(), captured.get(attacker.getUsername())::add);

        queue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo, queue, bus,
            MobRegistryTestSupport.random());
        if (encumbrance != null) {
            registry.setEncumbranceService(encumbrance);
        }
        registry.init();
        return registry;
    }

    /** Builds a registry with a two-member round-robin party (alice leader, bob member). */
    private MobRegistry buildRoundRobinRegistry(
        Player alice, Player bob, EncumbranceService encumbrance) {
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(goblin()));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK));
        ItemRepository itemRepo = new StubItemRepository(Map.of(DAGGER.getId(), DAGGER));

        roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(alice.getUsername());
        roomService.ensurePlayerLocation(bob.getUsername());

        playerRepo = new StubPlayerRepository(List.of(alice, bob));

        PlayerEventBus bus = new PlayerEventBus();
        for (Username u : List.of(alice.getUsername(), bob.getUsername())) {
            captured.put(u, new ArrayList<>());
            bus.register(u, captured.get(u)::add);
        }

        PartyService partyService = new PartyService();
        partyService.form(alice.getUsername());
        partyService.invite(alice.getUsername(), bob.getUsername(), true);
        partyService.accept(bob.getUsername());
        partyService.setLootMode(alice.getUsername(), LootMode.ROUND_ROBIN);

        queue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo, queue, bus,
            MobRegistryTestSupport.random());
        registry.setPartyService(partyService);
        if (encumbrance != null) {
            registry.setEncumbranceService(encumbrance);
        }
        registry.init();
        return registry;
    }

    private List<Item> savedInventory(Username username) {
        queue.flush(Duration.ofSeconds(5));
        return playerRepo.loadPlayer(username).orElseThrow().getInventory();
    }

    private List<String> capturedTexts(Username username) {
        return captured.get(username).stream()
            .flatMap(r -> r.messages().stream())
            .map(GameMessage::text)
            .toList();
    }

    // ── tests ─────────────────────────────────────────────────────────

    @Test
    void autoLootOn_withRoom_placesItemInInventory() {
        Player alice = player("Alice").withAutoLootEnabled(true);
        MobRegistry registry = buildSoloRegistry(alice, null);

        GameActionResult result = registry.processPlayerAttack(alice, "Goblin", ROOM_ID);

        assertTrue(result.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.equals("You loot a rusty dagger.")),
            "Alice should loot the drop into her inventory");
        assertFalse(result.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.contains("drops to the ground")),
            "Nothing should drop to the ground when autolooted");
        assertEquals(1, savedInventory(alice.getUsername()).size());
        assertTrue(roomService.findItem(alice.getUsername(), "rusty dagger").isEmpty(),
            "Nothing should be left on the floor");
    }

    @Test
    void autoLootOn_fullInventory_fallsBackToFloor() {
        Player alice = player("Alice").withAutoLootEnabled(true);
        // Alice is always overburdened, so she can never receive the item.
        EncumbranceService encumbrance = encumbrance(p -> true);
        MobRegistry registry = buildSoloRegistry(alice, encumbrance);

        GameActionResult result = registry.processPlayerAttack(alice, "Goblin", ROOM_ID);

        assertTrue(result.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.equals("A rusty dagger drops to the ground.")),
            "A full-inventory autoloot should fall back to a floor drop");
        assertEquals(0, savedInventory(alice.getUsername()).size());
        assertTrue(roomService.findItem(alice.getUsername(), "rusty dagger").isPresent(),
            "Dagger should be on the floor for Alice to GET");
    }

    @Test
    void autoLootOff_dropsToFloor() {
        Player alice = player("Alice"); // autoloot defaults to off
        MobRegistry registry = buildSoloRegistry(alice, null);

        GameActionResult result = registry.processPlayerAttack(alice, "Goblin", ROOM_ID);

        assertTrue(result.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.equals("A rusty dagger drops to the ground.")),
            "With autoloot off the drop should hit the floor");
        assertEquals(0, savedInventory(alice.getUsername()).size());
        assertTrue(roomService.findItem(alice.getUsername(), "rusty dagger").isPresent(),
            "Dagger should be on the floor for Alice to GET");
    }

    @Test
    void roundRobin_takesPrecedenceOverAutoLoot() {
        // Alice has autoloot on but is always full; in round-robin the item still routes to Bob
        // rather than falling to the floor, proving round-robin takes priority over autoloot.
        Player alice = player("Alice").withAutoLootEnabled(true);
        Player bob = player("Bob");
        EncumbranceService encumbrance = encumbrance(p -> p.getUsername().equals(alice.getUsername()));
        MobRegistry registry = buildRoundRobinRegistry(alice, bob, encumbrance);

        GameActionResult result = registry.processPlayerAttack(alice, "Goblin", ROOM_ID);

        assertTrue(result.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.equals("Bob loots a rusty dagger.")),
            "Round-robin should route the drop to Bob despite Alice's autoloot preference");
        assertFalse(result.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.contains("drops to the ground")),
            "Round-robin should not drop the item to the floor");
        assertTrue(capturedTexts(bob.getUsername()).stream()
                .anyMatch(t -> t.equals("You loot a rusty dagger.")),
            "Bob should loot the drop");
        assertEquals(0, savedInventory(alice.getUsername()).size());
        assertEquals(1, savedInventory(bob.getUsername()).size());
    }

    private EncumbranceService encumbrance(Predicate<Player> overburdened) {
        RaceRepository races = new RaceRepository() {
            @Override
            public Optional<Race> findById(RaceId id) {
                return Optional.empty();
            }

            @Override
            public List<Race> findAll() {
                return List.of();
            }
        };
        ClassRepository classes = new ClassRepository() {
            @Override
            public Optional<ClassDefinition> findById(ClassId id) {
                return Optional.empty();
            }

            @Override
            public List<ClassDefinition> findAll() {
                return List.of();
            }
        };
        return new EncumbranceService(races, classes) {
            @Override
            public boolean isOverburdened(Player player) {
                return overburdened.test(player);
            }
        };
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
        public Optional<AttackDefinition> findById(AttackId id) {
            return Optional.ofNullable(attacks.get(id));
        }
    }

    private static final class StubItemRepository implements ItemRepository {
        private final Map<ItemId, Item> items;

        StubItemRepository(Map<ItemId, Item> items) {
            this.items = Map.copyOf(items);
        }

        @Override
        public void save(Item item) {
        }

        @Override
        public Optional<Item> findById(ItemId id) {
            return Optional.ofNullable(items.get(id));
        }
    }

    private static final class StubPlayerRepository implements PlayerRepository {
        private final ConcurrentHashMap<Username, Player> store = new ConcurrentHashMap<>();

        StubPlayerRepository(List<Player> initial) {
            for (Player p : initial) {
                store.put(p.getUsername(), p);
            }
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
        private final Room room;

        StubRoomRepository(RoomId roomId) {
            this.room = new Room(roomId, "Test Room", "A featureless void.",
                Map.of(), List.of(), List.of());
        }

        @Override
        public void save(Room room) {
        }

        @Override
        public Optional<Room> findById(RoomId id) {
            return room.getId().equals(id) ? Optional.of(room) : Optional.empty();
        }
    }
}
