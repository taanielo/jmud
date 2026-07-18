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
import io.taanielo.jmud.core.party.PartyService;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Verifies that a mob's gold drop is split evenly across every eligible (alive, in-room) party
 * member using the same divisor and eligibility as the XP split, that dead members present in the
 * room still count toward the divisor but receive nothing, and that solo kills remain unchanged.
 */
class MobRegistryPartyGoldSplitTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId DEFAULT_ATTACK = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition ONE_DMG_ATTACK =
        new AttackDefinition(DEFAULT_ATTACK, "punch", 1, 1, 0, 0, 0, List.of());

    private final Map<Username, List<GameActionResult>> captured = new ConcurrentHashMap<>();
    private StubPlayerRepository playerRepo;
    private PersistenceQueue queue;

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    /** Creates a one-HP goblin that always drops exactly {@code gold} coins. */
    private MobTemplate goblin(int gold) {
        GoldDrop drop = new GoldDrop(gold, gold);
        return new MobTemplate(
            MobId.of("mob.goblin"), "Goblin", 1, DEFAULT_ATTACK, null, false,
            List.of(), ROOM_ID, 1, 10, 5, drop, null, false);
    }

    /** Creates a one-HP rat that drops no gold at all. */
    private MobTemplate ratNoGold() {
        return new MobTemplate(
            MobId.of("mob.rat"), "Rat", 1, DEFAULT_ATTACK, null, false,
            List.of(), ROOM_ID, 1, 10, 5, null, null, false);
    }

    /**
     * Builds a registry containing the given players. When {@code partied} is true the players form
     * a single party (first player is leader). Every player is placed in {@link #ROOM_ID}.
     */
    private MobRegistry buildRegistry(MobTemplate template, boolean partied, Player... players) {
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK));
        ItemRepository itemRepo = new StubItemRepository(Map.of());

        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        for (Player p : players) {
            roomService.ensurePlayerLocation(p.getUsername());
        }

        playerRepo = new StubPlayerRepository(List.of(players));

        PlayerEventBus bus = new PlayerEventBus();
        for (Player p : players) {
            captured.put(p.getUsername(), new ArrayList<>());
            bus.register(p.getUsername(), captured.get(p.getUsername())::add);
        }

        queue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo, queue, bus,
            MobRegistryTestSupport.random());

        if (partied) {
            PartyService partyService = new PartyService();
            Username leader = players[0].getUsername();
            partyService.form(leader);
            for (int i = 1; i < players.length; i++) {
                partyService.invite(leader, players[i].getUsername(), true);
                partyService.accept(players[i].getUsername());
            }
            registry.setPartyService(partyService);
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

    private Player saved(Username username) {
        queue.flush(Duration.ofSeconds(5));
        return playerRepo.loadPlayer(username).orElseThrow();
    }

    // ── tests ─────────────────────────────────────────────────────────

    @Test
    void soloKill_paysKillerFullRollWithLegacyMessage() {
        Player alice = player("Alice");
        MobRegistry registry = buildRegistry(goblin(9), false, alice);

        GameActionResult result = registry.processPlayerAttack(alice, "Goblin", ROOM_ID);

        // Solo message is unchanged byte-for-byte: no "You receive" split suffix.
        assertTrue(result.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.equals("The Goblin drops 9 gold coins.")),
            "Solo killer should see the legacy flat gold message: "
                + result.messages().stream().map(GameMessage::text).toList());
        assertEquals(9, saved(alice.getUsername()).getGold(), "Solo killer receives the full roll");
    }

    @Test
    void partyKill_splitsGoldEvenlyAcrossEligibleMembers() {
        Player alice = player("Alice");
        Player bob = player("Bob");
        Player carol = player("Carol");
        MobRegistry registry = buildRegistry(goblin(12), true, alice, bob, carol);

        GameActionResult result = registry.processPlayerAttack(alice, "Goblin", ROOM_ID);

        // 12 / 3 = 4 each; every member (including the killer) sees their own share.
        assertTrue(result.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.equals("The Goblin drops 12 gold coins. You receive 4.")),
            "Killer should see her own 4-coin share: "
                + result.messages().stream().map(GameMessage::text).toList());
        assertTrue(capturedTexts(bob.getUsername())
                .contains("The Goblin drops 12 gold coins. You receive 4."),
            "Bob should see his own 4-coin share");
        assertTrue(capturedTexts(carol.getUsername())
                .contains("The Goblin drops 12 gold coins. You receive 4."),
            "Carol should see her own 4-coin share");

        assertEquals(4, saved(alice.getUsername()).getGold());
        assertEquals(4, saved(bob.getUsername()).getGold());
        assertEquals(4, saved(carol.getUsername()).getGold());
    }

    @Test
    void partyKill_deadMemberCountsTowardDivisorButReceivesNothing() {
        Player alice = player("Alice");
        Player bob = player("Bob");
        // Carol is present in the room but dead: she counts toward the divisor (3) yet is paid nothing.
        Player carol = player("Carol").withDead(true);
        MobRegistry registry = buildRegistry(goblin(12), true, alice, bob, carol);

        GameActionResult result = registry.processPlayerAttack(alice, "Goblin", ROOM_ID);

        // Divisor is 3 (alive Alice + alive Bob + dead Carol), so each living share is 12 / 3 = 4.
        assertTrue(result.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.equals("The Goblin drops 12 gold coins. You receive 4.")),
            "Killer's share uses the full-party divisor including the dead member");

        assertEquals(4, saved(alice.getUsername()).getGold(), "Alice receives her share");
        assertEquals(4, saved(bob.getUsername()).getGold(), "Bob receives his share");
        assertEquals(0, saved(carol.getUsername()).getGold(), "Dead Carol receives no gold");
        // Only the 8 coins paid to the two living members leave the pot; the remaining 4 (the dead
        // member's slice) is not paid out, so the total never exceeds the roll.
        int totalPaid = saved(alice.getUsername()).getGold()
            + saved(bob.getUsername()).getGold()
            + saved(carol.getUsername()).getGold();
        assertTrue(totalPaid <= 12, "Total gold paid must never exceed the roll");
        assertFalse(capturedTexts(carol.getUsername()).stream()
                .anyMatch(t -> t.contains("gold coin")),
            "Dead Carol should not receive a gold message");
    }

    @Test
    void partyKill_remainderIsNotPaidOut() {
        Player alice = player("Alice");
        Player bob = player("Bob");
        Player carol = player("Carol");
        // 10 / 3 = 3 each (floor); the remaining 1 coin is dropped, not duplicated.
        MobRegistry registry = buildRegistry(goblin(10), true, alice, bob, carol);

        registry.processPlayerAttack(alice, "Goblin", ROOM_ID);

        assertEquals(3, saved(alice.getUsername()).getGold());
        assertEquals(3, saved(bob.getUsername()).getGold());
        assertEquals(3, saved(carol.getUsername()).getGold());
        int totalPaid = saved(alice.getUsername()).getGold()
            + saved(bob.getUsername()).getGold()
            + saved(carol.getUsername()).getGold();
        assertEquals(9, totalPaid, "Floor split pays 9 of the 10 coins; the remainder is not lost twice");
        assertTrue(totalPaid <= 10, "Total gold paid must never exceed the roll");
    }

    @Test
    void partyKill_zeroGoldMob_producesNoMessageAndNoGold() {
        Player alice = player("Alice");
        Player bob = player("Bob");
        MobRegistry registry = buildRegistry(ratNoGold(), true, alice, bob);

        GameActionResult result = registry.processPlayerAttack(alice, "Rat", ROOM_ID);

        assertFalse(result.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.contains("gold coin")),
            "A mob with no gold drop should emit no gold message to the killer");
        assertFalse(capturedTexts(bob.getUsername()).stream()
                .anyMatch(t -> t.contains("gold coin")),
            "A mob with no gold drop should emit no gold message to party members");
        assertEquals(0, saved(alice.getUsername()).getGold());
        assertEquals(0, saved(bob.getUsername()).getGold());
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

    private record StubItemRepository(Map<ItemId, Item> items) implements ItemRepository {
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
