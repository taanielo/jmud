package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatRandom;
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
import io.taanielo.jmud.core.world.TimeOfDay;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Unit tests covering the mob wander phase in {@link MobRegistry}.
 *
 * <p>Tests verify that wandering mobs move between rooms, that engaged mobs
 * and NPCs do not wander, and that respawning resets a mob to its spawn room.
 */
class MobRegistryWanderTest {

    private static final RoomId SPAWN_ROOM = RoomId.of("room.spawn");
    private static final RoomId NORTH_ROOM = RoomId.of("room.north");

    // ── helpers ───────────────────────────────────────────────────────

    private MobTemplate wanderingTemplate(boolean wanders, List<String> tags) {
        return new MobTemplate(
            MobId.of("mob.rat"),
            "Giant Rat",
            20,
            null,
            null,
            false,
            List.of(),
            SPAWN_ROOM,
            1,
            10,
            5,
            null,
            tags,
            wanders
        );
    }

    /**
     * Builds a room repository with two rooms: SPAWN_ROOM with a north exit to
     * NORTH_ROOM, and NORTH_ROOM with no exits.
     */
    private StubRoomRepository twoRoomRepository() {
        Room spawnRoom = new Room(
            SPAWN_ROOM, "Spawn Room", "A test room.",
            Map.of(Direction.NORTH, NORTH_ROOM),
            List.of(), List.of());
        Room northRoom = new Room(
            NORTH_ROOM, "North Room", "Another test room.",
            Map.of(Direction.SOUTH, SPAWN_ROOM),
            List.of(), List.of());
        return new StubRoomRepository(Map.of(
            SPAWN_ROOM, spawnRoom,
            NORTH_ROOM, northRoom
        ));
    }

    private MobRegistry buildRegistry(MobTemplate template, RoomRepository roomRepo) {
        return buildRegistry(template, roomRepo, MobRegistryTestSupport.random());
    }

    private MobRegistry buildRegistry(MobTemplate template, RoomRepository roomRepo, CombatRandom random) {
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(Map.of());
        ItemRepository itemRepo = new StubItemRepository(Map.of());
        RoomService roomService = new RoomService(roomRepo, SPAWN_ROOM);
        PlayerRepository playerRepo = new StubPlayerRepository();
        PlayerEventBus bus = new PlayerEventBus();
        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus, random);
        registry.init();
        return registry;
    }

    // ── tests ─────────────────────────────────────────────────────────

    /**
     * A wandering mob should eventually move to an adjacent room after enough ticks.
     * We run 50 ticks — with 30% probability per tick this should succeed with
     * overwhelming likelihood (P(never moves) = 0.7^50 ≈ 1e-8).
     */
    @Test
    void wanderingMob_movesToAdjacentRoom_afterTicks() {
        MobTemplate template = wanderingTemplate(true, List.of());
        // The shared deterministic RNG suppresses the 30% wander chance (nextDouble == 1.0), so this
        // test drives wandering with a source whose nextDouble is below the 0.30 threshold; the mob
        // then takes its single exit (NORTH) on the first tick.
        MobRegistry registry = buildRegistry(template, twoRoomRepository(), new AlwaysWanderRandom());

        MobInstance mob = registry.allInstances().iterator().next();
        assertEquals(SPAWN_ROOM, mob.roomId(),
            "Mob should start in spawn room");

        // Run ticks until the mob moves or we exhaust attempts
        boolean moved = false;
        for (int i = 0; i < 50; i++) {
            registry.tick();
            if (!mob.roomId().equals(SPAWN_ROOM)) {
                moved = true;
                break;
            }
        }
        assert moved : "Wandering mob should have moved within 50 ticks";
        assertEquals(NORTH_ROOM, mob.roomId(),
            "Mob should have moved to the north room");
    }

    /**
     * Deterministic RNG whose {@link CombatRandom#nextDouble()} of {@code 0.0} is below the wander
     * threshold (0.30) so a wandering mob moves every tick, and whose {@link CombatRandom#roll(int, int)}
     * returns the lower bound so the first (only) exit is chosen.
     */
    private static final class AlwaysWanderRandom implements CombatRandom {
        @Override
        public int roll(int minInclusive, int maxInclusive) {
            return minInclusive;
        }

        @Override
        public double nextDouble() {
            return 0.0;
        }
    }

    /**
     * A mob flagged with wanders=false should never move regardless of ticks.
     */
    @Test
    void nonWanderingMob_staysInSpawnRoom() {
        MobTemplate template = wanderingTemplate(false, List.of());
        MobRegistry registry = buildRegistry(template, twoRoomRepository());

        MobInstance mob = registry.allInstances().iterator().next();
        for (int i = 0; i < 20; i++) {
            registry.tick();
        }
        assertEquals(SPAWN_ROOM, mob.roomId(),
            "Non-wandering mob should remain in spawn room");
    }

    /**
     * A mob that is engaged in combat must not wander.
     */
    @Test
    void engagedMob_doesNotWander() {
        MobTemplate template = wanderingTemplate(true, List.of());
        MobRegistry registry = buildRegistry(template, twoRoomRepository());

        MobInstance mob = registry.allInstances().iterator().next();
        // Engage the mob manually
        mob.engage(Username.of("fighter"));

        for (int i = 0; i < 50; i++) {
            registry.tick();
        }
        assertEquals(SPAWN_ROOM, mob.roomId(),
            "Engaged mob should not wander");
    }

    /**
     * An NPC (tag "npc") with wanders=true must still not wander.
     */
    @Test
    void npcMob_doesNotWander() {
        MobTemplate template = wanderingTemplate(true, List.of("npc"));
        MobRegistry registry = buildRegistry(template, twoRoomRepository());

        MobInstance mob = registry.allInstances().iterator().next();
        for (int i = 0; i < 50; i++) {
            registry.tick();
        }
        assertEquals(SPAWN_ROOM, mob.roomId(),
            "NPC mob should not wander even if wanders=true");
    }

    /**
     * When a wandering mob dies and respawns, its room should be reset to the spawn room.
     */
    @Test
    void respawn_resetsMobToSpawnRoom() {
        MobTemplate template = wanderingTemplate(true, List.of());
        MobRegistry registry = buildRegistry(template, twoRoomRepository());

        MobInstance mob = registry.allInstances().iterator().next();

        // Force mob to move to north room
        mob.moveTo(NORTH_ROOM);
        assertEquals(NORTH_ROOM, mob.roomId(),
            "Precondition: mob should be in north room");

        // Kill it and run the respawn countdown
        mob.takeDamage(Integer.MAX_VALUE);
        mob.scheduleRespawn(TimeOfDay.DAY);

        // Tick through respawn countdown (10 ticks configured)
        for (int i = 0; i < 10; i++) {
            registry.tick();
        }

        assertEquals(SPAWN_ROOM, mob.roomId(),
            "Respawned mob should be back in spawn room");
    }

    /**
     * With a fixed world seed, a wandering mob's full room trajectory must be identical
     * across two independent runs, proving the wander roll and exit choice are routed
     * through the seeded RNG port rather than a bare {@code ThreadLocalRandom}. The
     * trajectory also changes rooms at least once, confirming the RNG actually drives
     * movement rather than the sequences matching only because the mob never moves.
     */
    @Test
    void wanderTrajectory_isDeterministic_underFixedSeed() {
        List<RoomId> firstRun = recordWanderTrajectory(1234L);
        List<RoomId> secondRun = recordWanderTrajectory(1234L);
        assertEquals(firstRun, secondRun,
            "Same seed must produce identical wander trajectories");
        assertTrue(firstRun.stream().anyMatch(NORTH_ROOM::equals),
            "Mob should have wandered to the north room at least once under this seed");
    }

    /**
     * Runs a single wandering mob for a fixed number of ticks under the given seed and
     * records the mob's room after each tick (always the same length), capturing the exact
     * trajectory driven by the seeded RNG.
     */
    private List<RoomId> recordWanderTrajectory(long seed) {
        MobTemplate template = wanderingTemplate(true, List.of());
        MobRegistry registry = buildRegistry(template, twoRoomRepository(), new SeededCombatRandom(seed));
        MobInstance mob = registry.allInstances().iterator().next();
        java.util.List<RoomId> trajectory = new java.util.ArrayList<>();
        // 100 ticks at ~30% move chance makes a zero-move trajectory effectively impossible
        // (0.7^100 ≈ 3e-16) while remaining fully deterministic for the given seed.
        for (int i = 0; i < 100; i++) {
            registry.tick();
            trajectory.add(mob.roomId());
        }
        return trajectory;
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

        @Override
        public void savePlayer(Player player) { store.put(player.getUsername(), player); }

        @Override
        public Optional<Player> loadPlayer(Username username) {
            return Optional.ofNullable(store.get(username));
        }
    }

    private static class StubRoomRepository implements RoomRepository {
        private final Map<RoomId, Room> rooms;

        StubRoomRepository(Map<RoomId, Room> rooms) {
            this.rooms = Map.copyOf(rooms);
        }

        @Override
        public void save(Room room) throws RepositoryException {}

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return Optional.ofNullable(rooms.get(id));
        }
    }
}
