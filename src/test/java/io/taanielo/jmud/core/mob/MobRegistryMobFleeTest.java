package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.SeededCombatRandom;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Unit tests for the mob-flee AI in {@link MobRegistry}: a badly wounded, non-boss, non-{@code
 * fearless} mob breaks off combat and flees to a random exit instead of fighting to the death.
 *
 * <p>Coverage: the happy path (below-threshold flee disengages attackers, moves the mob, and keeps
 * its low HP without a loot/XP/respawn payout), and every guard rail — world bosses never flee,
 * {@code fearless}-tagged mobs never flee, a dead-end room forbids fleeing, a failed roll fights on,
 * and the exit choice is deterministic under a fixed world seed.
 */
class MobRegistryMobFleeTest {

    private static final RoomId SPAWN_ROOM = RoomId.of("room.spawn");
    private static final RoomId NORTH_ROOM = RoomId.of("room.north");
    private static final RoomId EAST_ROOM = RoomId.of("room.east");
    private static final RoomId WEST_ROOM = RoomId.of("room.west");
    private static final AttackId DEFAULT_ATTACK = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition ONE_DMG_ATTACK =
        new AttackDefinition(DEFAULT_ATTACK, "punch", 1, 1, 0, 0, 0, List.of());

    // ── helpers ───────────────────────────────────────────────────────

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private MobTemplate mobTemplate(List<String> tags, boolean worldBoss) {
        return new MobTemplate(
            MobId.of("mob.goblin"),
            "Goblin",
            100,
            DEFAULT_ATTACK,
            null,
            true,          // aggressive: engages a player in its room
            List.of(),     // no loot table
            SPAWN_ROOM,
            1,
            10,
            5,
            null,
            tags,
            false,         // does not wander (keeps RNG focused on the flee rolls)
            null,
            null,
            false,
            null,
            null,
            worldBoss);
    }

    private StubRoomRepository twoRoomRepository() {
        Room spawn = new Room(SPAWN_ROOM, "Spawn", "A room.",
            Map.of(Direction.NORTH, NORTH_ROOM), List.of(), List.of());
        Room north = new Room(NORTH_ROOM, "North", "A room.",
            Map.of(Direction.SOUTH, SPAWN_ROOM), List.of(), List.of());
        return new StubRoomRepository(Map.of(SPAWN_ROOM, spawn, NORTH_ROOM, north));
    }

    private StubRoomRepository deadEndRepository() {
        Room spawn = new Room(SPAWN_ROOM, "Spawn", "A sealed room.",
            Map.of(), List.of(), List.of());
        return new StubRoomRepository(Map.of(SPAWN_ROOM, spawn));
    }

    private StubRoomRepository threeExitRepository() {
        Room spawn = new Room(SPAWN_ROOM, "Spawn", "A crossroads.",
            Map.of(Direction.NORTH, NORTH_ROOM, Direction.EAST, EAST_ROOM, Direction.WEST, WEST_ROOM),
            List.of(), List.of());
        Room north = new Room(NORTH_ROOM, "North", "A room.", Map.of(), List.of(), List.of());
        Room east = new Room(EAST_ROOM, "East", "A room.", Map.of(), List.of(), List.of());
        Room west = new Room(WEST_ROOM, "West", "A room.", Map.of(), List.of(), List.of());
        return new StubRoomRepository(Map.of(
            SPAWN_ROOM, spawn, NORTH_ROOM, north, EAST_ROOM, east, WEST_ROOM, west));
    }

    private record Fixture(MobRegistry registry, MobInstance mob, StubPlayerRepository players) {
    }

    private Fixture build(MobTemplate template, RoomRepository roomRepo, Player target, CombatRandom random) {
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(DEFAULT_ATTACK, ONE_DMG_ATTACK));
        ItemRepository itemRepo = new StubItemRepository();
        RoomService roomService = new RoomService(roomRepo, SPAWN_ROOM);
        roomService.ensurePlayerLocation(target.getUsername());
        StubPlayerRepository playerRepo = new StubPlayerRepository(target);
        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), new PlayerEventBus(), random);
        registry.init();
        MobInstance mob = registry.allInstances().iterator().next();
        return new Fixture(registry, mob, playerRepo);
    }

    // ── tests ─────────────────────────────────────────────────────────

    @Test
    void woundedMob_flees_disengagingAttackers_movingToExit_keepingLowHp() {
        Player hero = player("hero");
        Fixture f = build(mobTemplate(List.of(), false), twoRoomRepository(), hero,
            MobRegistryTestSupport.random());
        f.registry().setMobFleeSettings(20, 100);

        // First tick: mob is at full HP (above the flee threshold), so it engages and attacks.
        f.registry().tick();
        assertTrue(f.registry().isInCombat(hero.getUsername()),
            "Precondition: aggressive mob should have engaged the hero");

        // Wound the mob below the 20% threshold (100 max HP -> threshold 20).
        f.mob().takeDamage(85);
        assertTrue(f.mob().currentHp() <= 20, "Precondition: mob should be below the flee threshold");

        // Second tick: the mob rolls a guaranteed flee.
        f.registry().tick();

        assertFalse(f.registry().isInCombat(hero.getUsername()),
            "Fleeing mob must disengage every attacker");
        assertTrue(f.mob().engagedPlayers().isEmpty(), "Fleeing mob must clear its engaged set");
        assertEquals(NORTH_ROOM, f.mob().roomId(), "Fleeing mob must move to a valid exit room");
        assertTrue(f.mob().isAlive(), "A fled mob is not defeated");
        assertTrue(f.mob().currentHp() > 0 && f.mob().currentHp() <= 20,
            "Fleeing keeps the mob's current low HP; it does not heal or reset");
        assertEquals(0L, f.players().loadPlayer(hero.getUsername()).orElseThrow().getExperience(),
            "A fled mob grants no XP");
    }

    @Test
    void worldBossMob_neverFlees() {
        Player hero = player("hero");
        Fixture f = build(mobTemplate(List.of(), true), twoRoomRepository(), hero,
            MobRegistryTestSupport.random());
        f.registry().setMobFleeSettings(100, 100);   // would always flee if allowed
        f.mob().engage(hero.getUsername());
        f.mob().takeDamage(90);

        for (int i = 0; i < 5; i++) {
            f.registry().tick();
        }

        assertEquals(SPAWN_ROOM, f.mob().roomId(), "A world boss must fight to the death and never flee");
    }

    @Test
    void fearlessMob_neverFlees() {
        Player hero = player("hero");
        Fixture f = build(mobTemplate(List.of("undead", "fearless"), false), twoRoomRepository(), hero,
            MobRegistryTestSupport.random());
        f.registry().setMobFleeSettings(100, 100);
        f.mob().engage(hero.getUsername());
        f.mob().takeDamage(90);

        for (int i = 0; i < 5; i++) {
            f.registry().tick();
        }

        assertEquals(SPAWN_ROOM, f.mob().roomId(), "A fearless mob must never flee");
    }

    @Test
    void mobInDeadEndRoom_cannotFlee_andKeepsFighting() {
        Player hero = player("hero");
        Fixture f = build(mobTemplate(List.of(), false), deadEndRepository(), hero,
            MobRegistryTestSupport.random());
        f.registry().setMobFleeSettings(100, 100);
        f.mob().engage(hero.getUsername());
        f.mob().takeDamage(90);

        f.registry().tick();

        assertEquals(SPAWN_ROOM, f.mob().roomId(), "A mob with no exits cannot flee");
        assertTrue(f.registry().isInCombat(hero.getUsername()),
            "A cornered mob keeps fighting: it engages/attacks instead of fleeing");
    }

    @Test
    void mob_doesNotFlee_whenChanceRollFails() {
        Player hero = player("hero");
        Fixture f = build(mobTemplate(List.of(), false), twoRoomRepository(), hero,
            MobRegistryTestSupport.random());
        f.registry().setMobFleeSettings(20, 0);      // never rolls a successful flee
        f.mob().engage(hero.getUsername());
        f.mob().takeDamage(90);

        for (int i = 0; i < 5; i++) {
            f.registry().tick();
        }

        assertEquals(SPAWN_ROOM, f.mob().roomId(),
            "With a 0% flee chance a wounded mob always fights on");
    }

    @Test
    void fleeExitChoice_isDeterministic_underFixedSeed() {
        RoomId firstRun = recordFleeDestination(4242L);
        RoomId secondRun = recordFleeDestination(4242L);
        assertEquals(firstRun, secondRun,
            "Same world seed must drive the flee exit choice to the same room");
        assertNotEquals(SPAWN_ROOM, firstRun,
            "The mob should actually have fled (RNG drove the move) rather than stayed put");
    }

    private RoomId recordFleeDestination(long seed) {
        Player hero = player("hero");
        Fixture f = build(mobTemplate(List.of(), false), threeExitRepository(), hero,
            new SeededCombatRandom(seed));
        f.registry().setMobFleeSettings(20, 100);
        f.mob().engage(hero.getUsername());
        f.mob().takeDamage(90);
        f.registry().tick();
        return f.mob().roomId();
    }

    // ── stubs ─────────────────────────────────────────────────────────

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
        @Override
        public void save(Item item) throws RepositoryException {
        }

        @Override
        public Optional<Item> findById(ItemId id) throws RepositoryException {
            return Optional.empty();
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
        private final Map<RoomId, Room> rooms;

        StubRoomRepository(Map<RoomId, Room> rooms) {
            this.rooms = Map.copyOf(rooms);
        }

        @Override
        public void save(Room room) throws RepositoryException {
        }

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return Optional.ofNullable(rooms.get(id));
        }
    }
}
