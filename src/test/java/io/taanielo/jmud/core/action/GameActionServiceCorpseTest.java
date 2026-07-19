package io.taanielo.jmud.core.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.AbilityCooldownTracker;
import io.taanielo.jmud.core.ability.AbilityCostResolver;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityRegistry;
import io.taanielo.jmud.core.ability.AbilityTargetResolver;
import io.taanielo.jmud.core.ability.BasicAbilityCostResolver;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.character.repository.ClassRepositoryException;
import io.taanielo.jmud.core.character.repository.RaceRepository;
import io.taanielo.jmud.core.character.repository.RaceRepositoryException;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatEngine;
import io.taanielo.jmud.core.combat.CombatModifierResolver;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.ThreadLocalCombatRandom;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.player.EncumbranceService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomPathfinder;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.area.Area;
import io.taanielo.jmud.core.world.area.AreaId;
import io.taanielo.jmud.core.world.area.AreaRepository;
import io.taanielo.jmud.core.world.area.CorpseLocatorService;
import io.taanielo.jmud.core.world.area.WayfindService;
import io.taanielo.jmud.core.world.area.WorldAtlas;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Unit tests for {@link GameActionService#corpse(Player, String)}: the argument dispatch across bare
 * {@code CORPSE}, {@code CORPSE ALL}, and {@code CORPSE <n>}. The output formatting itself is covered
 * by {@code CorpseLocatorServiceTest}; these tests verify the branching, count line, and invalid-index
 * handling wire through correctly.
 */
class GameActionServiceCorpseTest {

    private static final int DECAY_SECONDS = 300;
    private static final RoomId START = RoomId.of("start");
    private static final RoomId ZONE_A = RoomId.of("zone-a");
    private static final RoomId ZONE_B = RoomId.of("zone-b");

    private RoomService roomService;
    private Player player;

    @BeforeEach
    void setUp() {
        Room start = new Room(START, "Start", "The start.", Map.of(), List.of(), List.of());
        Room zoneA = new Room(ZONE_A, "Zone A", "Zone A.", Map.of(), List.of(), List.of());
        Room zoneB = new Room(ZONE_B, "Zone B", "Zone B.", Map.of(), List.of(), List.of());
        roomService = new RoomService(
            new TestRoomRepository(Map.of(START, start, ZONE_A, zoneA, ZONE_B, zoneB)), START);
        player = player("hero");
        roomService.movePlayerTo(player.getUsername(), START);
    }

    @Test
    void bareCorpseWithNoneReportsNoCorpse() {
        GameActionResult result = service().corpse(player, "");

        assertEquals(1, result.messages().size());
        assertEquals("You have no corpse in the world.", result.messages().getFirst().text());
    }

    @Test
    void bareCorpseWithMultipleReportsMostUrgentAndCountLine() {
        roomService.spawnCorpse(player.getUsername(), ZONE_A, 10);
        roomService.spawnCorpse(player.getUsername(), ZONE_B, 20);

        List<String> lines = texts(service().corpse(player, ""));

        assertTrue(lines.stream().anyMatch(l -> l.contains("Zone A")),
            "most urgent (older) corpse reported first");
        assertTrue(lines.contains("You have 2 corpses in the world. Type CORPSE ALL to list them."));
    }

    @Test
    void corpseAllListsEveryCorpse() {
        roomService.spawnCorpse(player.getUsername(), ZONE_A, 10);
        roomService.spawnCorpse(player.getUsername(), ZONE_B, 20);

        List<String> lines = texts(service().corpse(player, "ALL"));

        assertEquals("You have 2 corpses in the world (soonest to decay first):", lines.getFirst());
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("  1.")));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("  2.")));
    }

    @Test
    void corpseIndexReportsRequestedCorpse() {
        roomService.spawnCorpse(player.getUsername(), ZONE_A, 10);
        roomService.spawnCorpse(player.getUsername(), ZONE_B, 20);

        List<String> lines = texts(service().corpse(player, "2"));

        assertTrue(lines.getFirst().contains("Zone B"), "second corpse is in Zone B");
    }

    @Test
    void corpseIndexOutOfRangeReportsError() {
        roomService.spawnCorpse(player.getUsername(), ZONE_A, 10);
        roomService.spawnCorpse(player.getUsername(), ZONE_B, 20);

        List<String> lines = texts(service().corpse(player, "5"));

        assertEquals(
            List.of("You do not have a corpse #5. You have 2 corpses — try CORPSE ALL."), lines);
    }

    @Test
    void unrecognizedArgumentReportsUsage() {
        roomService.spawnCorpse(player.getUsername(), ZONE_A, 10);

        List<String> lines = texts(service().corpse(player, "bogus"));

        assertEquals(List.of("Usage: CORPSE, CORPSE ALL, or CORPSE <number>."), lines);
    }

    private static List<String> texts(GameActionResult result) {
        return result.messages().stream().map(GameMessage::text).toList();
    }

    private GameActionService service() {
        AbilityRegistry registry = new AbilityRegistry(List.of());
        AbilityCostResolver costResolver = new BasicAbilityCostResolver();
        EffectEngine effectEngine = new EffectEngine(new StubEffectRepository(Map.of()));
        AbilityTargetResolver resolver = (source, input) -> Optional.empty();
        GameActionService service = new GameActionService(
            registry, costResolver, effectEngine, defaultCombat(), roomService,
            resolver, new TestCooldowns(), testEncumbranceService(), _ -> false);
        service.setCorpseLocatorService(corpseLocator());
        return service;
    }

    private CorpseLocatorService corpseLocator() {
        Function<RoomId, Map<Direction, RoomId>> exits = roomId -> Map.of();
        Function<RoomId, String> names = roomId -> switch (roomId.getValue()) {
            case "zone-a" -> "Zone A";
            case "zone-b" -> "Zone B";
            case "start" -> "Start";
            default -> roomId.getValue();
        };
        WayfindService wayfindService = new WayfindService(
            new EmptyAreaRepository(), new RoomPathfinder(), List::of, exits, names);
        return new CorpseLocatorService(wayfindService, names, () -> DECAY_SECONDS, Instant::now);
    }

    private CombatEngine defaultCombat() {
        AttackId defaultAttack = CombatSettings.defaultAttackId();
        AttackDefinition attack = new AttackDefinition(defaultAttack, "punch", 2, 4, 0, 0, 0, List.of());
        return new CombatEngine(
            new StubAttackRepository(Map.of(defaultAttack, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            new ThreadLocalCombatRandom());
    }

    private static Player player(String username) {
        PlayerVitals vitals = new PlayerVitals(30, 30, 20, 20, 30, 30);
        return new Player(
            User.of(Username.of(username), Password.hash("pw", 1000)),
            1, 0, vitals, List.of(), "prompt", false,
            List.<AbilityId>of(), null, null);
    }

    private static EncumbranceService testEncumbranceService() {
        return new EncumbranceService(new StubRaceRepository(), new StubClassRepository()) {
            @Override
            public boolean isOverburdened(Player player) {
                return false;
            }
        };
    }

    private static class TestCooldowns implements AbilityCooldownTracker {
        @Override
        public boolean isOnCooldown(AbilityId abilityId) {
            return false;
        }

        @Override
        public int remainingTicks(AbilityId abilityId) {
            return 0;
        }

        @Override
        public void startCooldown(AbilityId abilityId, int ticks) {
        }
    }

    private static class StubRaceRepository implements RaceRepository {
        @Override
        public Optional<Race> findById(RaceId id) throws RaceRepositoryException {
            return Optional.empty();
        }

        @Override
        public List<Race> findAll() throws RaceRepositoryException {
            return List.of();
        }
    }

    private static class StubClassRepository implements ClassRepository {
        @Override
        public Optional<ClassDefinition> findById(ClassId id) throws ClassRepositoryException {
            return Optional.empty();
        }

        @Override
        public List<ClassDefinition> findAll() throws ClassRepositoryException {
            return List.of();
        }
    }

    private static class StubAttackRepository implements AttackRepository {
        private final Map<AttackId, AttackDefinition> attacks;

        StubAttackRepository(Map<AttackId, AttackDefinition> attacks) {
            this.attacks = attacks;
        }

        @Override
        public Optional<AttackDefinition> findById(AttackId id) throws RepositoryException {
            return Optional.ofNullable(attacks.get(id));
        }
    }

    private static class StubEffectRepository implements EffectRepository {
        private final Map<EffectId, EffectDefinition> definitions;

        StubEffectRepository(Map<EffectId, EffectDefinition> definitions) {
            this.definitions = definitions;
        }

        @Override
        public Optional<EffectDefinition> findById(EffectId id) {
            return Optional.ofNullable(definitions.get(id));
        }
    }

    private record EmptyAreaRepository() implements AreaRepository {
        @Override
        public List<Area> findAll() {
            return List.of();
        }

        @Override
        public Optional<Area> findById(AreaId id) {
            return Optional.empty();
        }

        @Override
        public Optional<WorldAtlas> findAtlas() {
            return Optional.empty();
        }
    }

    private record TestRoomRepository(Map<RoomId, Room> rooms) implements RoomRepository {
        private TestRoomRepository(Map<RoomId, Room> rooms) {
            this.rooms = new ConcurrentHashMap<>(rooms);
        }

        @Override
        public void save(Room room) throws RepositoryException {
            rooms.put(room.getId(), room);
        }

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return Optional.ofNullable(rooms.get(id));
        }
    }
}
