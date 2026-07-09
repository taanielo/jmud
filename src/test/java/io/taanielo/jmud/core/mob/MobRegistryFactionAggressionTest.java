package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
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
import io.taanielo.jmud.core.faction.PlayerReputation;
import io.taanielo.jmud.core.faction.ReputationService;
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
 * Verifies that a faction-affiliated mob's decision to initiate combat is driven by the target
 * player's reputation with that faction: it engages a hostile player but leaves a neutral one alone.
 */
class MobRegistryFactionAggressionTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final FactionId BANDITS = FactionId.of("bandits");
    private static final AttackId DEFAULT_ATTACK = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition ONE_DMG_ATTACK =
        new AttackDefinition(DEFAULT_ATTACK, "punch", 1, 1, 0, 0, 0, List.of());
    private static final Faction BANDIT_FACTION =
        new Faction(BANDITS, "the Bandit Brotherhood", "Cutthroats.", -10, 0, 0.0);

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private MobRegistry buildRegistry(Player target, boolean wireReputation) {
        // A non-aggressive bandit: whether it engages is decided purely by faction hostility.
        MobTemplate template = new MobTemplate(
            MobId.of("mob.bandit"),
            "Bandit",
            100,
            DEFAULT_ATTACK,
            null,
            false,           // not inherently aggressive
            List.of(),
            ROOM_ID,
            1,
            10,
            5,
            null,
            List.of(),
            false,
            null,
            null,
            false,
            null,
            BANDITS          // faction
        );
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK));
        ItemRepository itemRepo = new StubItemRepository(Map.of());

        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(target.getUsername());

        StubPlayerRepository playerRepo = new StubPlayerRepository(target);
        PlayerEventBus bus = new PlayerEventBus();

        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus, MobRegistryTestSupport.random());
        if (wireReputation) {
            registry.setReputationService(reputationService());
        }
        registry.init();
        return registry;
    }

    private static ReputationService reputationService() {
        try {
            return new ReputationService(new StubFactionRepository(List.of(BANDIT_FACTION)));
        } catch (FactionRepositoryException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void factionMob_doesNotEngageNeutralPlayer() {
        Player neutral = player("hero");
        MobRegistry registry = buildRegistry(neutral, true);

        registry.tick();

        assertFalse(registry.isInCombat(neutral.getUsername()),
            "A faction mob must not attack a player it is not hostile toward");
    }

    @Test
    void factionMob_engagesHostilePlayer() {
        Player hostile = player("outlaw")
            .withReputation(PlayerReputation.empty().adjust(BANDITS, -10));
        MobRegistry registry = buildRegistry(hostile, true);

        registry.tick();

        assertTrue(registry.isInCombat(hostile.getUsername()),
            "A faction mob must attack a player its faction is hostile toward");
    }

    @Test
    void factionMob_staysPassiveWhenReputationDisabled() {
        Player hostile = player("outlaw")
            .withReputation(PlayerReputation.empty().adjust(BANDITS, -10));
        MobRegistry registry = buildRegistry(hostile, false);

        registry.tick();

        assertFalse(registry.isInCombat(hostile.getUsername()),
            "Without a reputation service a non-aggressive faction mob does not initiate combat");
    }

    @Test
    void slayingFactionMob_lowersKillerReputationWithThatFaction() {
        Player attacker = player("hero");
        MobTemplate template = new MobTemplate(
            MobId.of("mob.bandit"),
            "Bandit",
            1,               // one-shot kill
            DEFAULT_ATTACK,
            null,
            false,
            List.of(),
            ROOM_ID,
            1,
            10,
            5,
            null,
            List.of(),
            false,
            null,
            null,
            false,
            null,
            BANDITS);
        StubMobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        StubAttackRepository attackRepo = new StubAttackRepository(Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK));
        StubItemRepository itemRepo = new StubItemRepository(Map.of());
        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(attacker.getUsername());
        StubPlayerRepository playerRepo = new StubPlayerRepository(attacker);
        PersistenceQueue persistenceQueue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo, persistenceQueue,
            new PlayerEventBus(), MobRegistryTestSupport.random());
        registry.setReputationService(reputationService());
        registry.init();

        GameActionResult result = registry.processPlayerAttack(attacker, "Bandit", ROOM_ID);

        List<String> texts = result.messages().stream().map(GameMessage::text).toList();
        assertTrue(texts.stream().anyMatch(t -> t.contains("reputation") && t.contains("decreases")),
            "Expected a reputation-decrease message, got: " + texts);

        assertTrue(persistenceQueue.flush(Duration.ofSeconds(2)));
        persistenceQueue.close();
        Player saved = playerRepo.loadPlayer(attacker.getUsername()).orElseThrow();
        assertTrue(saved.reputation().standing(BANDITS) < 0,
            "Killing a bandit should lower the killer's standing with the bandits");
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
        private final Room room;

        StubRoomRepository(RoomId roomId) {
            this.room = new Room(
                roomId, "Test Room", "A featureless void.", Map.of(), List.of(), List.of());
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
