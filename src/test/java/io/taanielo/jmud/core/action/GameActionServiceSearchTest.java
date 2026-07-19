package io.taanielo.jmud.core.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.AbilityCooldownTracker;
import io.taanielo.jmud.core.ability.AbilityCostResolver;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityRegistry;
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
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.CombatSettings;
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
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Unit tests for {@link GameActionService#searchForHiddenExits(Player)} covering the SEARCH command:
 * the roll outcome, the world-visible reveal, combat gating, and stealth breaking.
 */
class GameActionServiceSearchTest {

    private static final RoomId ROOM_A = RoomId.of("a");
    private static final RoomId ROOM_B = RoomId.of("b");

    private RoomService roomService;
    private final Username alice = Username.of("Alice");

    @BeforeEach
    void setUp() {
        Room roomA = new Room(
            ROOM_A, "Room A", "A quiet room.",
            Map.of(), List.of(), List.of(),
            Map.of(), null, null, null, false, List.of(),
            Map.of(Direction.DOWN, ROOM_B));
        Room roomB = new Room(ROOM_B, "Room B", "A hidden vault.", Map.of(Direction.UP, ROOM_A),
            List.of(), List.of());
        roomService = new RoomService(new TestRoomRepository(Map.of(ROOM_A, roomA, ROOM_B, roomB)), ROOM_A);
        roomService.ensurePlayerLocation(alice);
    }

    @Test
    void hiddenExitIsInvisibleAndUnusableBeforeDiscovery() {
        assertFalse(exitsLine().contains("down"), "Hidden exit must not be listed before discovery");
        RoomService.MoveResult move = roomService.move(alice, Direction.DOWN);
        assertFalse(move.moved(), "Undiscovered hidden exit must not be walkable");
    }

    @Test
    void successfulSearchRevealsHiddenExitForEveryone() {
        // nextDouble() derives from roll(0, 999999) / 1_000_000; roll 0 => 0.0 < 0.5 (success).
        GameActionService service = service(GameActionServiceSearchTest::notInCombat, 0);

        GameActionResult result = service.searchForHiddenExits(player(alice));

        assertTrue(messageText(result).contains("hidden passage"),
            "A successful search should announce the discovery");
        assertTrue(exitsLine().contains("down"), "Revealed exit should now appear in the exits line");
        RoomService.MoveResult move = roomService.move(alice, Direction.DOWN);
        assertTrue(move.moved(), "Revealed hidden exit should be walkable");
    }

    @Test
    void missReportsNothingNewAndKeepsExitHidden() {
        // roll 999999 => ~0.999999 >= 0.5 (miss).
        GameActionService service = service(GameActionServiceSearchTest::notInCombat, 999_999);

        GameActionResult result = service.searchForHiddenExits(player(alice));

        assertTrue(messageText(result).contains("nothing new"));
        assertFalse(exitsLine().contains("down"), "A missed search must not reveal the exit");
    }

    @Test
    void fullyDiscoveredRoomReportsNothingLeftToFind() {
        roomService.revealHiddenExits(alice);
        GameActionService service = service(GameActionServiceSearchTest::notInCombat, 0);

        GameActionResult result = service.searchForHiddenExits(player(alice));

        assertTrue(messageText(result).contains("nothing new"),
            "Searching a fully discovered room finds nothing");
    }

    @Test
    void searchIsRejectedWhileInCombat() {
        GameActionService service = service(source -> true, 0);

        GameActionResult result = service.searchForHiddenExits(player(alice));

        assertTrue(messageText(result).contains("too busy fighting"));
        assertFalse(exitsLine().contains("down"), "A rejected search must not reveal the exit");
    }

    @Test
    void searchBreaksStealth() {
        GameActionService service = service(GameActionServiceSearchTest::notInCombat, 0);
        Player hidden = player(alice).withStealth(true);

        GameActionResult result = service.searchForHiddenExits(hidden);

        assertFalse(result.updatedSource().isStealthActive(), "Searching should break stealth");
        assertTrue(messageText(result).contains("emerge from the shadows"));
    }

    @Test
    void nonRogueChanceIsFlatFiftyPercent() {
        assertEquals(0.5d, GameActionService.calculateSearchSuccessChance(false, 1), 1e-9);
        assertEquals(0.5d, GameActionService.calculateSearchSuccessChance(false, 50), 1e-9,
            "Non-rogue chance must not scale with level");
    }

    @Test
    void rogueChanceIsHigherThanBaseAndScalesWithLevel() {
        double level1 = GameActionService.calculateSearchSuccessChance(true, 1);
        double level5 = GameActionService.calculateSearchSuccessChance(true, 5);
        assertTrue(level1 > 0.5d, "A level-1 rogue must beat the flat 50% base chance");
        assertTrue(level5 > level1, "Rogue chance must increase with level");
    }

    @Test
    void rogueChanceIsCappedBelowCertainty() {
        double veryHighLevel = GameActionService.calculateSearchSuccessChance(true, 1000);
        assertTrue(veryHighLevel < 1.0d, "A search must never be guaranteed");
        assertEquals(0.9d, veryHighLevel, 1e-9, "Rogue chance is capped at the max");
    }

    @Test
    void rogueSucceedsOnARollANonRogueWouldMiss() {
        // Rogue level 10 => 0.5 + 0.02*10 = 0.70 success chance.
        // nextDouble() = 600000/1_000_000 = 0.6: below 0.70 (rogue hit) but at/above 0.5 (non-rogue miss).
        GameActionService service = service(GameActionServiceSearchTest::notInCombat, 600_000);

        GameActionResult rogueResult = service.searchForHiddenExits(rogueOfLevel(alice, 10));

        assertTrue(messageText(rogueResult).contains("hidden passage"),
            "A high-level rogue should succeed where the flat base chance would miss");
        assertTrue(exitsLine().contains("down"), "Rogue's successful search should reveal the exit");
    }

    @Test
    void nonRogueMissesOnTheSameRollTheRogueHits() {
        // nextDouble() = 0.6 >= 0.5 base chance: a non-rogue misses on the roll a level-10 rogue clears.
        GameActionService service = service(GameActionServiceSearchTest::notInCombat, 600_000);

        GameActionResult result = service.searchForHiddenExits(player(alice));

        assertTrue(messageText(result).contains("nothing new"),
            "A non-rogue must miss on a roll above the flat base chance");
        assertFalse(exitsLine().contains("down"), "A missed search must not reveal the exit");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Player rogueOfLevel(Username username, int level) {
        return new Player(
            User.of(username, Password.hash("pw", 1000)),
            level, 0L, PlayerVitals.defaults(), List.of(), "prompt", false,
            List.of(), null, ClassId.of("rogue"));
    }


    private String exitsLine() {
        return roomService.look(alice).lines().stream()
            .filter(line -> line.startsWith("Exits:"))
            .findFirst()
            .orElse("");
    }

    private static String messageText(GameActionResult result) {
        StringBuilder sb = new StringBuilder();
        for (GameMessage message : result.messages()) {
            sb.append(message.text()).append('\n');
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean notInCombat(Player player) {
        return false;
    }

    private GameActionService service(Predicate<Player> inCombat, int fixedRoll) {
        AttackId defaultAttack = CombatSettings.defaultAttackId();
        AttackDefinition attack = new AttackDefinition(defaultAttack, "punch", 2, 4, 0, 0, 0, List.of());
        CombatEngine combatEngine = new CombatEngine(
            new StubAttackRepository(Map.of(defaultAttack, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            new FixedCombatRandom(fixedRoll));
        EffectEngine effectEngine = new EffectEngine(new StubEffectRepository(Map.of()));
        AbilityRegistry abilityRegistry = new AbilityRegistry(List.of());
        AbilityCostResolver costResolver = new BasicAbilityCostResolver();
        return new GameActionService(
            abilityRegistry, costResolver, effectEngine, combatEngine, roomService,
            (source, input) -> Optional.empty(), new TestCooldowns(), testEncumbranceService(),
            inCombat, new FixedCombatRandom(fixedRoll), source -> { });
    }

    private static Player player(Username username) {
        return Player.of(User.of(username, Password.hash("pw", 1000)), "prompt", false);
    }

    private static EncumbranceService testEncumbranceService() {
        return new EncumbranceService(new StubRaceRepository(), new StubClassRepository()) {
            @Override
            public boolean isOverburdened(Player player) {
                return false;
            }
        };
    }

    private record TestRoomRepository(Map<RoomId, Room> rooms) implements RoomRepository {
        TestRoomRepository(Map<RoomId, Room> rooms) {
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

    private static final class FixedCombatRandom implements CombatRandom {
        private final int value;

        FixedCombatRandom(int value) {
            this.value = value;
        }

        @Override
        public int roll(int minInclusive, int maxInclusive) {
            return value;
        }
    }

    private static final class TestCooldowns implements AbilityCooldownTracker {
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

    private static final class StubRaceRepository implements RaceRepository {
        @Override
        public Optional<Race> findById(RaceId id) throws RaceRepositoryException {
            return Optional.empty();
        }

        @Override
        public List<Race> findAll() throws RaceRepositoryException {
            return List.of();
        }
    }

    private static final class StubClassRepository implements ClassRepository {
        @Override
        public Optional<ClassDefinition> findById(ClassId id) throws ClassRepositoryException {
            return Optional.empty();
        }

        @Override
        public List<ClassDefinition> findAll() throws ClassRepositoryException {
            return List.of();
        }
    }

    private static final class StubAttackRepository implements AttackRepository {
        private final Map<AttackId, AttackDefinition> attacks;

        StubAttackRepository(Map<AttackId, AttackDefinition> attacks) {
            this.attacks = attacks;
        }

        @Override
        public Optional<AttackDefinition> findById(AttackId id) throws RepositoryException {
            return Optional.ofNullable(attacks.get(id));
        }
    }

    private static final class StubEffectRepository implements EffectRepository {
        private final Map<EffectId, EffectDefinition> definitions;

        StubEffectRepository(Map<EffectId, EffectDefinition> definitions) {
            this.definitions = definitions;
        }

        @Override
        public Optional<EffectDefinition> findById(EffectId id) {
            return Optional.ofNullable(definitions.get(id));
        }
    }
}
