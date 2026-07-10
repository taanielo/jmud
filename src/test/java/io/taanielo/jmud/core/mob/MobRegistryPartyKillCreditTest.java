package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import io.taanielo.jmud.core.faction.Faction;
import io.taanielo.jmud.core.faction.FactionId;
import io.taanielo.jmud.core.faction.FactionRepository;
import io.taanielo.jmud.core.faction.FactionRepositoryException;
import io.taanielo.jmud.core.faction.ReputationService;
import io.taanielo.jmud.core.party.PartyService;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.quest.ActiveQuest;
import io.taanielo.jmud.core.quest.QuestId;
import io.taanielo.jmud.core.quest.QuestKillService;
import io.taanielo.jmud.core.quest.QuestRepository;
import io.taanielo.jmud.core.quest.QuestTemplate;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Verifies that mob-kill quest progress and faction reputation credit are shared with every eligible
 * party member present in the room (issue #395), consistent with the existing XP/loot split — not just
 * the member who lands the killing blow.
 */
class MobRegistryPartyKillCreditTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId DEFAULT_ATTACK = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition ONE_DMG_ATTACK =
        new AttackDefinition(DEFAULT_ATTACK, "punch", 1, 1, 0, 0, 0, List.of());

    private static final String GOBLIN_MOB_ID = "goblin";
    private static final QuestId GOBLIN_HUNT_ID = QuestId.of("goblin-hunt");
    private static final QuestTemplate GOBLIN_HUNT = new QuestTemplate(
        GOBLIN_HUNT_ID, "Goblin Hunter", "Kill 6 goblins.", GOBLIN_MOB_ID, 6, 30, 75);
    private static final QuestId RAT_HUNT_ID = QuestId.of("rat-hunt");
    private static final QuestTemplate RAT_HUNT = new QuestTemplate(
        RAT_HUNT_ID, "Rat Catcher", "Kill 5 rats.", "rat", 5, 20, 50);

    private static final FactionId GOBLINS = FactionId.of("goblins");
    private static final Faction GOBLIN_FACTION =
        new Faction(GOBLINS, "the Goblin Horde", "Vicious raiders.", -10, 0, 0.0);

    private final Map<Username, List<GameActionResult>> captured = new ConcurrentHashMap<>();
    private StubPlayerRepository playerRepo;
    private PersistenceQueue queue;

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private MobTemplate goblin(FactionId factionId) {
        return new MobTemplate(
            MobId.of(GOBLIN_MOB_ID), "Goblin", 1, DEFAULT_ATTACK, null, false,
            List.of(), ROOM_ID, 1, 10, 5, null, List.of(), false, null, null, false, null, factionId);
    }

    /**
     * Builds a registry containing the given players. When {@code partied} is true the players form a
     * single party (first player is leader). Quest and reputation services are always wired.
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
        registry.setQuestKillService(new QuestKillService(
            new StubQuestRepository(List.of(GOBLIN_HUNT, RAT_HUNT))));
        registry.setReputationService(reputationService());

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

    private static ReputationService reputationService() {
        try {
            return new ReputationService(new StubFactionRepository(List.of(GOBLIN_FACTION)));
        } catch (FactionRepositoryException e) {
            throw new IllegalStateException(e);
        }
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
    void partyMembers_bothProgressSameKillQuest() {
        Player alice = player("Alice").withActiveQuest(new ActiveQuest(GOBLIN_HUNT_ID, 6));
        Player bob = player("Bob").withActiveQuest(new ActiveQuest(GOBLIN_HUNT_ID, 6));
        MobRegistry registry = buildRegistry(goblin(null), true, alice, bob);

        GameActionResult result = registry.processPlayerAttack(alice, "Goblin", ROOM_ID);

        // Attacker sees her own quest progress inline.
        assertTrue(result.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.equals("Goblin Hunter: 1/6 kills.")),
            "Alice (killer) should see her own quest progress");
        // Party member sees his own quest progress via the event bus.
        assertTrue(capturedTexts(bob.getUsername()).contains("Goblin Hunter: 1/6 kills."),
            "Bob should see his own quest progress from the shared kill");

        ActiveQuest aliceQuest = saved(alice.getUsername()).getActiveQuest();
        ActiveQuest bobQuest = saved(bob.getUsername()).getActiveQuest();
        assertNotNull(aliceQuest);
        assertNotNull(bobQuest);
        assertEquals(5, aliceQuest.killsRemaining(), "Alice's quest counter should tick down");
        assertEquals(5, bobQuest.killsRemaining(), "Bob's quest counter should also tick down");
    }

    @Test
    void partyMember_withoutMatchingQuest_isUnaffected() {
        Player alice = player("Alice").withActiveQuest(new ActiveQuest(GOBLIN_HUNT_ID, 6));
        // Bob has a different quest that does not target goblins.
        Player bob = player("Bob").withActiveQuest(new ActiveQuest(RAT_HUNT_ID, 5));
        MobRegistry registry = buildRegistry(goblin(null), true, alice, bob);

        GameActionResult result = registry.processPlayerAttack(alice, "Goblin", ROOM_ID);

        assertTrue(result.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.equals("Goblin Hunter: 1/6 kills.")),
            "Alice should still progress her goblin quest");
        assertFalse(capturedTexts(bob.getUsername()).stream().anyMatch(t -> t.contains("kills.")),
            "Bob's unrelated rat quest should produce no progress message");

        ActiveQuest bobQuest = saved(bob.getUsername()).getActiveQuest();
        assertNotNull(bobQuest);
        assertEquals(5, bobQuest.killsRemaining(), "Bob's unrelated quest counter must be untouched");
    }

    @Test
    void soloKill_progressesOnlyKiller() {
        Player alice = player("Alice").withActiveQuest(new ActiveQuest(GOBLIN_HUNT_ID, 6));
        MobRegistry registry = buildRegistry(goblin(null), false, alice);

        GameActionResult result = registry.processPlayerAttack(alice, "Goblin", ROOM_ID);

        assertTrue(result.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.equals("Goblin Hunter: 1/6 kills.")),
            "Solo killer should still see her quest progress");
        ActiveQuest aliceQuest = saved(alice.getUsername()).getActiveQuest();
        assertNotNull(aliceQuest);
        assertEquals(5, aliceQuest.killsRemaining(), "Solo kill still decrements the killer's quest");
    }

    @Test
    void partyMembers_bothGainReputationFromFactionMob() {
        Player alice = player("Alice");
        Player bob = player("Bob");
        MobRegistry registry = buildRegistry(goblin(GOBLINS), true, alice, bob);

        GameActionResult result = registry.processPlayerAttack(alice, "Goblin", ROOM_ID);

        assertTrue(result.messages().stream().map(GameMessage::text)
                .anyMatch(t -> t.contains("reputation") && t.contains("decreases")),
            "Alice (killer) should see her reputation change");
        assertTrue(capturedTexts(bob.getUsername()).stream()
                .anyMatch(t -> t.contains("reputation") && t.contains("decreases")),
            "Bob should also see his reputation change from the shared kill");

        assertTrue(saved(alice.getUsername()).reputation().standing(GOBLINS) < 0,
            "Alice's standing with the goblins should fall");
        assertTrue(saved(bob.getUsername()).reputation().standing(GOBLINS) < 0,
            "Bob's standing with the goblins should also fall");
    }

    // ── stubs ─────────────────────────────────────────────────────────

    private record StubFactionRepository(List<Faction> factions) implements FactionRepository {
        @Override
        public List<Faction> findAll() {
            return factions;
        }

        @Override
        public Optional<Faction> findById(FactionId factionId) {
            return factions.stream().filter(f -> f.id().equals(factionId)).findFirst();
        }
    }

    private record StubQuestRepository(List<QuestTemplate> templates) implements QuestRepository {
        @Override
        public List<QuestTemplate> findAll() {
            return templates;
        }

        @Override
        public Optional<QuestTemplate> findById(QuestId id) {
            return templates.stream().filter(t -> t.id().equals(id)).findFirst();
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
