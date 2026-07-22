package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.action.GameActionResult;
import io.taanielo.jmud.core.action.GameMessage;
import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
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
 * Verifies the boss reinforcement-wave mechanic (issue #809): the first committed AI decision on which
 * a reinforcement-capable boss's HP has dropped to/below its authored threshold summons a wave of
 * existing lower-tier adds into the boss's room and announces it, exactly once per encounter; an
 * ordinary mob with no reinforcement fields is completely unaffected.
 */
class MobRegistryReinforcementTest {

    private static final RoomId ROOM_A = RoomId.of("room.a");
    private static final RoomId ROOM_B = RoomId.of("room.b");

    private static final AttackId BASIC_ATTACK = AttackId.of("attack.basic");
    private static final AttackDefinition BASIC_MELEE =
        new AttackDefinition(BASIC_ATTACK, "strike", 1, 1, 0, 0, 0, List.of());
    private static final Map<AttackId, AttackDefinition> ATTACKS = Map.of(BASIC_ATTACK, BASIC_MELEE);

    private static final MobId ADD_ID = MobId.of("mob.add");
    private static final String REINFORCE_LINE = "calls out, and fresh voices coalesce to its defence!";

    private final List<Harness> harnesses = new ArrayList<>();

    @AfterEach
    void closeHarnesses() {
        harnesses.forEach(Harness::close);
        harnesses.clear();
    }

    @Test
    void woundingBossPastThresholdSummonsAddsIntoItsRoomOnce() {
        Harness h = new Harness(List.of(reinforcementBoss(50, 3), add()));
        MobInstance boss = h.mob("the Boss");
        boss.engage(h.playerName); // already engaged, so the AI decision is not a first engagement
        boss.takeDamage(60); // 100 -> 40, below the 50% reinforcement threshold

        assertEquals(0, h.addsInRoom(ROOM_A), "no adds are in the boss room before the wave fires");

        h.tick(); // committed AI decision crosses the threshold and summons the wave

        assertEquals(3, h.addsInRoom(ROOM_A), "the wave spawns reinforcement_count adds into the boss room");
        assertEquals(1, h.reinforceMessageCount(), "the wave announces exactly once");
        assertTrue(h.contains("The Boss " + REINFORCE_LINE), "the announcement names the boss");
    }

    @Test
    void reinforcementFiresOnlyOncePerEncounter() {
        Harness h = new Harness(List.of(reinforcementBoss(50, 2), add()));
        MobInstance boss = h.mob("the Boss");
        boss.engage(h.playerName);
        boss.takeDamage(70); // 100 -> 30, below the threshold

        h.tick();
        h.tick();
        h.tick();

        assertEquals(2, h.addsInRoom(ROOM_A), "a second wave never stacks — the count stays at the first wave");
        assertEquals(1, h.reinforceMessageCount(), "the wave announces only once per encounter");
    }

    @Test
    void ordinaryBossNeverSummonsReinforcements() {
        Harness h = new Harness(List.of(nonReinforcementBoss(), add()));
        MobInstance boss = h.mob("the Boss");
        boss.engage(h.playerName);
        boss.takeDamage(90); // 100 -> 10, deep below any threshold

        for (int i = 0; i < 4; i++) {
            h.tick();
        }

        assertEquals(0, h.addsInRoom(ROOM_A), "a mob with no reinforcement fields never summons adds");
        assertEquals(0, h.reinforceMessageCount(), "no reinforcement announcement fires for an ordinary mob");
    }

    // ── templates ─────────────────────────────────────────────────────

    private static MobTemplate reinforcementBoss(int hpPercent, int count) {
        return new MobTemplate(
            MobId.of("mob.boss"), "the Boss", 100, BASIC_ATTACK, null, true,
            List.of(), ROOM_A, 1, 10, 5, null, List.of(), false,
            null, null, false, null, null, false, false, 0, Map.of(), Map.of(), null,
            null, 1.0, hpPercent, ADD_ID, count);
    }

    private static MobTemplate nonReinforcementBoss() {
        return new MobTemplate(
            MobId.of("mob.boss"), "the Boss", 100, BASIC_ATTACK, null, true,
            List.of(), ROOM_A, 1, 10, 5, null, List.of(), false);
    }

    private static MobTemplate add() {
        // Spawns at start-up into ROOM_B (away from the boss) so counting adds in ROOM_A reflects only
        // reinforcements. Aggressive per its own template, exactly as the mechanic requires.
        return new MobTemplate(
            ADD_ID, "a lesser voice", 40, BASIC_ATTACK, null, true,
            List.of(), ROOM_B, 1, 10, 5, null, List.of(), false);
    }

    private static Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    // ── harness ───────────────────────────────────────────────────────

    private final class Harness implements AutoCloseable {
        private final MobRegistry registry;
        private final PersistenceQueue persistenceQueue;
        private final Username playerName;
        private final List<GameMessage> published = new ArrayList<>();

        Harness(List<MobTemplate> templates) {
            harnesses.add(this);
            Player player = player("hero");
            this.playerName = player.getUsername();
            MobTemplateRepository templateRepo = new StubMobTemplateRepository(templates);
            AttackRepository attackRepo = new StubAttackRepository(ATTACKS);
            RoomService roomService = new RoomService(new StubRoomRepository(), ROOM_A);
            roomService.ensurePlayerLocation(playerName);
            StubPlayerRepository playerRepo = new StubPlayerRepository(player);
            PlayerEventBus bus = new PlayerEventBus();
            bus.register(playerName, this::capture);
            this.persistenceQueue = MobRegistryTestSupport.persistenceQueueFor(playerRepo);
            this.registry = new MobRegistry(
                templateRepo, new StubItemRepository(), attackRepo, roomService, playerRepo,
                persistenceQueue, bus,
                MobRegistryTestSupport.random());
            registry.init();
            // Keep a wounded boss committing its AI decision rather than breaking off to flee (#567),
            // so the reinforcement check — which sits after the flee gate — is exercised deterministically.
            registry.setMobFleeSettings(0, 0);
        }

        void tick() {
            registry.tick();
            if (!persistenceQueue.flush(Duration.ofSeconds(5))) {
                throw new IllegalStateException("Persistence queue did not drain within 5s");
            }
        }

        @Override
        public void close() {
            persistenceQueue.close();
        }

        private void capture(GameActionResult result) {
            published.addAll(result.messages());
        }

        MobInstance mob(String name) {
            return registry.getMobsInRoom(ROOM_A).stream()
                .filter(m -> m.template().name().equals(name))
                .findFirst()
                .orElseThrow();
        }

        int addsInRoom(RoomId roomId) {
            return (int) registry.getMobsInRoom(roomId).stream()
                .filter(m -> m.template().id().equals(ADD_ID))
                .count();
        }

        int reinforceMessageCount() {
            return (int) published.stream().filter(m -> m.text().contains(REINFORCE_LINE)).count();
        }

        boolean contains(String needle) {
            return published.stream().anyMatch(m -> m.text().contains(needle));
        }
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
        public Optional<AttackDefinition> findById(AttackId id) throws RepositoryException {
            return Optional.ofNullable(attacks.get(id));
        }
    }

    private static final class StubItemRepository implements ItemRepository {
        @Override
        public void save(Item item) {
        }

        @Override
        public Optional<Item> findById(ItemId id) {
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
        private final Map<RoomId, Room> rooms = Map.of(
            ROOM_A, new Room(
                ROOM_A, "Room A", "A ruined courtyard.", Map.of(Direction.NORTH, ROOM_B),
                List.of(), List.of()),
            ROOM_B, new Room(
                ROOM_B, "Room B", "A collapsed tower.", Map.of(Direction.SOUTH, ROOM_A),
                List.of(), List.of())
        );

        @Override
        public void save(Room room) {
        }

        @Override
        public Optional<Room> findById(RoomId id) {
            return Optional.ofNullable(rooms.get(id));
        }
    }
}
