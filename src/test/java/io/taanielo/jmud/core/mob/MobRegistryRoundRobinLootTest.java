package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Integration tests for round-robin party loot on mob kills: rotation across kills, full-inventory
 * fallback to the next member, and the all-full fallback to a floor drop.
 */
class MobRegistryRoundRobinLootTest {

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

    private MobTemplate goblin(int maxCount) {
        return new MobTemplate(
            MobId.of("mob.goblin"), "Goblin", 1, null, null, false,
            List.of(new LootEntry(DAGGER.getId(), 1.0)),
            ROOM_ID, maxCount, 10, 5, null, null, false);
    }

    /** Builds a wired registry with a two-member round-robin party (alice leader, bob member). */
    private MobRegistry buildRegistry(
        MobTemplate template, Player alice, Player bob, EncumbranceService encumbrance) {
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
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

    private List<String> capturedTexts(Username username) {
        return captured.get(username).stream()
            .flatMap(r -> r.messages().stream())
            .map(GameMessage::text)
            .toList();
    }

    private List<Item> savedInventory(Username username) {
        queue.flush(Duration.ofSeconds(5));
        return playerRepo.loadPlayer(username).orElseThrow().getInventory();
    }

    // ── tests ─────────────────────────────────────────────────────────

    @Test
    void roundRobin_rotatesRecipientAcrossKills() {
        Player alice = player("Alice");
        Player bob = player("Bob");
        MobRegistry registry = buildRegistry(goblin(2), alice, bob, null);

        // First kill: leader (index 0) is next in the rotation.
        GameActionResult first = registry.processPlayerAttack(alice, "Goblin", ROOM_ID);
        assertTrue(first.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.equals("You loot a rusty dagger.")),
            "Alice should loot the first drop");
        assertTrue(capturedTexts(bob.getUsername()).stream()
                .anyMatch(t -> t.equals("Alice loots a rusty dagger.")),
            "Bob should see Alice loot the first drop");

        // Flush so the second kill's reload of Alice sees her freshly-saved (dagger-carrying) state.
        queue.flush(Duration.ofSeconds(5));

        // Second kill: rotation advances to Bob.
        GameActionResult second = registry.processPlayerAttack(alice, "Goblin", ROOM_ID);
        assertTrue(second.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.equals("Bob loots a rusty dagger.")),
            "Alice should see Bob loot the second drop");
        assertTrue(capturedTexts(bob.getUsername()).stream()
                .anyMatch(t -> t.equals("You loot a rusty dagger.")),
            "Bob should loot the second drop");

        assertEquals(1, savedInventory(alice.getUsername()).size());
        assertEquals(1, savedInventory(bob.getUsername()).size());
    }

    @Test
    void roundRobin_skipsFullMemberInFavourOfNext() {
        Player alice = player("Alice");
        Player bob = player("Bob");
        // Alice's inventory is always full; Bob can always receive.
        EncumbranceService encumbrance = encumbrance(p -> p.getUsername().equals(alice.getUsername()));
        MobRegistry registry = buildRegistry(goblin(1), alice, bob, encumbrance);

        GameActionResult result = registry.processPlayerAttack(alice, "Goblin", ROOM_ID);

        // Even though it was Alice's turn, she is full so Bob receives it.
        assertTrue(result.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.equals("Bob loots a rusty dagger.")),
            "Alice (full) should be skipped and see Bob loot it");
        assertTrue(capturedTexts(bob.getUsername()).stream()
                .anyMatch(t -> t.equals("You loot a rusty dagger.")),
            "Bob should loot the drop");
        assertEquals(0, savedInventory(alice.getUsername()).size());
        assertEquals(1, savedInventory(bob.getUsername()).size());
    }

    @Test
    void roundRobin_fallsBackToFloorWhenNobodyCanCarry() {
        Player alice = player("Alice");
        Player bob = player("Bob");
        // Nobody can carry anything.
        EncumbranceService encumbrance = encumbrance(p -> true);
        MobRegistry registry = buildRegistry(goblin(1), alice, bob, encumbrance);

        GameActionResult result = registry.processPlayerAttack(alice, "Goblin", ROOM_ID);

        assertTrue(result.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.contains("No one has room for the rusty dagger")),
            "Alice should be told the item dropped to the floor");
        assertEquals(0, savedInventory(alice.getUsername()).size());
        assertEquals(0, savedInventory(bob.getUsername()).size());
        // The item is on the room floor.
        assertTrue(roomService.findItem(alice.getUsername(), "rusty dagger").isPresent(),
            "Dagger should be on the floor for anyone to GET");
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
