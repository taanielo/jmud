package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
import io.taanielo.jmud.core.combat.CombatRandom;
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
 * Integration tests for roll party loot on mob kills: the highest 1-100 roll wins each drop, ties
 * re-roll among only the tied members, and an unclaimable item still falls to the floor. Rolls are
 * driven through a scripted {@link CombatRandom} so outcomes are deterministic.
 */
class MobRegistryRollLootTest {

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

    /**
     * Builds a wired registry with a roll-mode party led by the first member. The scripted random
     * yields {@code rollSequence} for every {@code roll(1, 100)} call in order — the first two entries
     * cover the killing swing's hit/crit rolls, the rest drive the loot rolls.
     */
    private MobRegistry buildRegistry(
        List<Player> members, EncumbranceService encumbrance, int... rollSequence) {
        MobTemplate template = goblin();
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK));
        ItemRepository itemRepo = new StubItemRepository(Map.of(DAGGER.getId(), DAGGER));

        roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        for (Player member : members) {
            roomService.ensurePlayerLocation(member.getUsername());
        }

        playerRepo = new StubPlayerRepository(members);

        PlayerEventBus bus = new PlayerEventBus();
        for (Player member : members) {
            captured.put(member.getUsername(), new ArrayList<>());
            bus.register(member.getUsername(), captured.get(member.getUsername())::add);
        }

        PartyService partyService = new PartyService();
        Username leader = members.get(0).getUsername();
        partyService.form(leader);
        for (int i = 1; i < members.size(); i++) {
            partyService.invite(leader, members.get(i).getUsername(), true);
            partyService.accept(members.get(i).getUsername());
        }
        partyService.setLootMode(leader, LootMode.ROLL);

        queue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo, queue, bus,
            new ScriptedCombatRandom(rollSequence));
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
    void roll_highestRollWinsTheItem() {
        Player alice = player("Alice");
        Player bob = player("Bob");
        // hit=50, crit=90 (no crit), then loot rolls: Alice 80, Bob 30.
        MobRegistry registry = buildRegistry(List.of(alice, bob), null, 50, 90, 80, 30);

        GameActionResult result = registry.processPlayerAttack(alice, "Goblin", ROOM_ID);

        String expected = "Alice rolls 80, Bob rolls 30 for the rusty dagger... Alice wins the roll!";
        assertTrue(result.messages().stream().map(GameMessage::text).anyMatch(t -> t.equals(expected)),
            "Alice should see the roll breakdown naming her the winner");
        assertTrue(capturedTexts(bob.getUsername()).contains(expected),
            "Bob should see the same roll breakdown");
        assertEquals(1, savedInventory(alice.getUsername()).size());
        assertEquals(0, savedInventory(bob.getUsername()).size());
    }

    @Test
    void roll_tieReRollsAmongOnlyTiedMembers() {
        Player alice = player("Alice");
        Player bob = player("Bob");
        Player cara = player("Cara");
        // hit=50, crit=90; round 1: Alice 90, Bob 90, Cara 10 (Cara out); re-roll: Alice 55, Bob 40.
        // The trailing 99 is a sentinel: were Cara wrongly re-rolled she would win with it.
        MobRegistry registry =
            buildRegistry(List.of(alice, bob, cara), null, 50, 90, 90, 90, 10, 55, 40, 99);

        GameActionResult result = registry.processPlayerAttack(alice, "Goblin", ROOM_ID);

        List<String> aliceTexts = result.messages().stream().map(GameMessage::text).toList();
        assertTrue(aliceTexts.stream().anyMatch(t ->
                t.contains("Alice rolls 90, Bob rolls 90, Cara rolls 10")
                    && t.contains("tie at 90")
                    && t.contains("Alice rolls 55, Bob rolls 40")
                    && t.contains("Alice wins the roll!")),
            "Roll breakdown should show the tie, the re-roll among the tied pair, and Alice winning");
        // Only the tied members (Alice, Bob) re-roll; Cara is excluded, so Alice wins — not Cara.
        assertEquals(1, savedInventory(alice.getUsername()).size());
        assertEquals(0, savedInventory(bob.getUsername()).size());
        assertEquals(0, savedInventory(cara.getUsername()).size());
    }

    @Test
    void roll_fallsBackToFloorWhenNobodyCanCarry() {
        Player alice = player("Alice");
        Player bob = player("Bob");
        // Nobody can carry anything, so no loot rolls happen: only the hit/crit rolls are scripted.
        EncumbranceService encumbrance = encumbrance(p -> true);
        MobRegistry registry = buildRegistry(List.of(alice, bob), encumbrance, 50, 90);

        GameActionResult result = registry.processPlayerAttack(alice, "Goblin", ROOM_ID);

        assertTrue(result.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.contains("No one has room for the rusty dagger")),
            "Alice should be told the item dropped to the floor");
        assertEquals(0, savedInventory(alice.getUsername()).size());
        assertEquals(0, savedInventory(bob.getUsername()).size());
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

    /**
     * Deterministic RNG that yields a scripted sequence for {@code roll(1, 100)} (falling back to the
     * lower bound once exhausted) and a boundary {@code nextDouble()} of {@code 1.0} so the loot-drop
     * gate always fires.
     */
    private static final class ScriptedCombatRandom implements CombatRandom {
        private final Deque<Integer> rolls = new ArrayDeque<>();

        ScriptedCombatRandom(int... values) {
            for (int value : values) {
                rolls.add(value);
            }
        }

        @Override
        public int roll(int minInclusive, int maxInclusive) {
            Integer next = rolls.poll();
            int value = next == null ? minInclusive : next;
            return Math.max(minInclusive, Math.min(maxInclusive, value));
        }

        @Override
        public double nextDouble() {
            return 1.0;
        }
    }

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
