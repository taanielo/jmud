package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.action.NpcStealPort;
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
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Unit tests verifying that an aggressive mob automatically initiates combat
 * against a player in the same room on the next tick.
 */
class MobRegistryAggressiveMobTest {

    private static final RoomId ROOM_ID = RoomId.of("room.test");
    private static final AttackId DEFAULT_ATTACK =
        AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition ONE_DMG_ATTACK =
        new AttackDefinition(DEFAULT_ATTACK, "punch", 1, 1, 0, 0, 0, List.of());

    private StubPlayerRepository lastPlayerRepo;

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    /**
     * Builds a {@link MobRegistry} containing a single aggressive mob with a
     * valid attack definition, and places the given player in the mob's room.
     */
    private MobRegistry buildRegistryWithAggressiveMob(Player target, int mobHp) {
        MobTemplate template = new MobTemplate(
            MobId.of("mob.spider"),
            "Giant Spider",
            mobHp,
            DEFAULT_ATTACK,
            null,
            true,   // aggressive
            List.of(),
            ROOM_ID,
            1,
            10,
            5,
            null,
            null,
            false
        );
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK));
        ItemRepository itemRepo = new StubItemRepository(Map.of());

        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(target.getUsername());

        StubPlayerRepository playerRepo = new StubPlayerRepository(target);
        lastPlayerRepo = playerRepo;
        PlayerEventBus bus = new PlayerEventBus();

        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus, MobRegistryTestSupport.random());
        registry.init();
        return registry;
    }

    @Test
    void aggressiveMob_addsPlayerToCombatTargets_afterTick() {
        Player target = player("hero");
        MobRegistry registry = buildRegistryWithAggressiveMob(target, 100);

        assertFalse(registry.isInCombat(target.getUsername()),
            "Player should not be in combat before the first tick");

        registry.tick();

        assertTrue(registry.isInCombat(target.getUsername()),
            "Player should be in combat after the aggressive mob's first tick");
    }

    @Test
    void aggressiveMob_playerCanFleeAfterAggressiveEngagement() {
        Player target = player("hero");
        MobRegistry registry = buildRegistryWithAggressiveMob(target, 100);

        registry.tick();
        assertTrue(registry.isInCombat(target.getUsername()),
            "Precondition: aggressive mob should have engaged the player");

        registry.fleeCombat(target.getUsername());

        assertFalse(registry.isInCombat(target.getUsername()),
            "Player should no longer be in combat after fleeing from aggressive mob");
    }

    @Test
    void aggressiveMob_doesNotEngageStealthedPlayer_afterTick() {
        Player hidden = player("sneak").withStealth(true);
        MobRegistry registry = buildRegistryWithAggressiveMob(hidden, 100);

        registry.tick();

        assertFalse(registry.isInCombat(hidden.getUsername()),
            "An aggressive mob must not engage a player hidden in stealth");
    }

    @Test
    void aggressiveMob_keepsAttackingAlreadyEngagedPlayerEvenWhenStealthed() {
        Player hero = player("hero");
        MobRegistry registry = buildRegistryWithAggressiveMob(hero, 100);

        registry.tick();
        assertTrue(registry.isInCombat(hero.getUsername()),
            "Precondition: mob should have engaged the player");

        // The player slips into stealth after already being engaged; existing combat continues.
        lastPlayerRepo.savePlayer(hero.withStealth(true));
        registry.tick();

        assertTrue(registry.isInCombat(hero.getUsername()),
            "Stealth only prevents fresh aggro; an already-engaged mob keeps attacking");
    }

    /**
     * Builds a {@link MobRegistry} containing a single non-aggressive NPC named "Bandit" with the
     * given gold drop, and places the given player in the NPC's room. Used to exercise the rogue
     * STEAL port ({@link NpcStealPort}).
     */
    private MobRegistry buildRegistryWithGoldNpc(Player thief, GoldDrop goldDrop) {
        MobTemplate template = new MobTemplate(
            MobId.of("mob.bandit"),
            "Bandit",
            30,
            DEFAULT_ATTACK,
            null,
            false,  // not aggressive: only becomes hostile when caught stealing
            List.of(),
            ROOM_ID,
            1,
            10,
            5,
            goldDrop,
            List.of(),
            false
        );
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK));
        ItemRepository itemRepo = new StubItemRepository(Map.of());

        RoomService roomService = new RoomService(new StubRoomRepository(ROOM_ID), ROOM_ID);
        roomService.ensurePlayerLocation(thief.getUsername());

        StubPlayerRepository playerRepo = new StubPlayerRepository(thief);
        lastPlayerRepo = playerRepo;
        PlayerEventBus bus = new PlayerEventBus();

        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus, MobRegistryTestSupport.random());
        registry.init();
        return registry;
    }

    @Test
    void findStealTarget_returnsVictimWithStealableGold() {
        Player thief = player("rogue");
        MobRegistry registry = buildRegistryWithGoldNpc(thief, new GoldDrop(15, 15));

        Optional<NpcStealPort.StealVictim> victim = registry.findStealTarget(ROOM_ID, "bandit");

        assertTrue(victim.isPresent());
        assertTrue(victim.get().hasStealableGold());
        assertTrue(victim.get().stealGold() == 15);
    }

    @Test
    void findStealTarget_reportsNoGoldWhenTemplateHasNoGoldDrop() {
        Player thief = player("rogue");
        MobRegistry registry = buildRegistryWithGoldNpc(thief, null);

        Optional<NpcStealPort.StealVictim> victim = registry.findStealTarget(ROOM_ID, "bandit");

        assertTrue(victim.isPresent());
        assertFalse(victim.get().hasStealableGold());
    }

    @Test
    void findStealTarget_isEmptyForUnknownNpc() {
        Player thief = player("rogue");
        MobRegistry registry = buildRegistryWithGoldNpc(thief, new GoldDrop(15, 15));

        assertFalse(registry.findStealTarget(ROOM_ID, "dragon").isPresent());
    }

    @Test
    void turnHostile_putsThiefIntoCombatWithNpc() {
        Player thief = player("rogue");
        MobRegistry registry = buildRegistryWithGoldNpc(thief, new GoldDrop(15, 15));

        assertFalse(registry.isInCombat(thief.getUsername()),
            "Precondition: a non-aggressive NPC does not engage on its own");

        registry.findStealTarget(ROOM_ID, "bandit").orElseThrow().turnHostile(thief.getUsername());

        assertTrue(registry.isInCombat(thief.getUsername()),
            "A caught thief must be engaged by the NPC they tried to rob");
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

        @Override
        public void save(Item item) throws RepositoryException {}

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
        public void savePlayer(Player player) { store.put(player.getUsername(), player); }

        @Override
        public Optional<Player> loadPlayer(Username username) {
            return Optional.ofNullable(store.get(username));
        }
    }

    private static class StubRoomRepository implements RoomRepository {
        private final Room room;

        StubRoomRepository(RoomId roomId) {
            this.room = new Room(
                roomId, "Test Room", "A featureless void.", Map.of(), List.of(), List.of());
        }

        @Override
        public void save(Room room) throws RepositoryException {}

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return room.getId().equals(id) ? Optional.of(room) : Optional.empty();
        }
    }
}
