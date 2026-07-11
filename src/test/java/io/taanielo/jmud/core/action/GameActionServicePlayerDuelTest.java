package io.taanielo.jmud.core.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
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
import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.player.DuelService;
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
 * Unit tests for the consensual player-vs-player duel flow on {@link GameActionService} and its
 * backing {@link DuelService}: challenge validation, acceptance, timeout expiry, duel-end loot/XP
 * suppression, flee blocking, and the non-duel PvP death path remaining unchanged.
 */
class GameActionServicePlayerDuelTest {

    private static final RoomId ROOM_A = RoomId.of("a");
    private static final RoomId ROOM_B = RoomId.of("b");

    private RoomService roomService;
    private DuelService duelService;
    private Player attacker;
    private Player target;

    @BeforeEach
    void setUp() {
        Room roomA = new Room(
            ROOM_A, "Room A", "A quiet room.",
            Map.of(Direction.NORTH, ROOM_B), List.of(), List.of()
        );
        Room roomB = new Room(
            ROOM_B, "Room B", "Another room.",
            Map.of(Direction.SOUTH, ROOM_A), List.of(), List.of()
        );
        roomService = new RoomService(new TestRoomRepository(Map.of(ROOM_A, roomA, ROOM_B, roomB)), ROOM_A);
        duelService = new DuelService();

        attacker = player("attacker");
        target = player("target");

        roomService.ensurePlayerLocation(attacker.getUsername());
        roomService.ensurePlayerLocation(target.getUsername());
    }

    // ── initiate ───────────────────────────────────────────────────────────

    @Test
    void initiateDuelSendsChallengeAndRecordsPendingRequest() {
        GameActionService service = service(defaultCombat(), resolver(target), _ -> false);

        GameActionResult result = service.initiatePlayerDuel(attacker, "target");

        assertTrue(result.messages().stream().anyMatch(m ->
            m.type() == GameMessage.Type.SOURCE && m.text().equals("You challenge target to a duel.")));
        assertTrue(result.messages().stream().anyMatch(m ->
            m.type() == GameMessage.Type.PLAYER
                && m.text().equals(
                    "attacker challenges you to a duel. Type ACCEPT to engage or wait 30s for timeout.")));
        assertEquals(
            Optional.of(attacker.getUsername()),
            duelService.pendingChallenger(target.getUsername()));
    }

    @Test
    void initiateDuelRejectsBlankName() {
        GameActionService service = service(defaultCombat(), resolver(target), _ -> false);

        GameActionResult result = service.initiatePlayerDuel(attacker, "  ");

        assertEquals("Usage: duel <player>", result.messages().getFirst().text());
    }

    @Test
    void initiateDuelRejectsMissingTarget() {
        GameActionService service = service(defaultCombat(), resolver(target), _ -> false);

        GameActionResult result = service.initiatePlayerDuel(attacker, "ghost");

        assertEquals("There is no one here by that name to duel.", result.messages().getFirst().text());
    }

    @Test
    void initiateDuelRejectsSelfTarget() {
        GameActionService service = service(defaultCombat(), resolver(target, attacker), _ -> false);

        GameActionResult result = service.initiatePlayerDuel(attacker, "attacker");

        assertEquals("You cannot duel yourself.", result.messages().getFirst().text());
    }

    @Test
    void initiateDuelRejectedWhenInitiatorInMobCombat() {
        Predicate<Player> inCombat = p -> p.getUsername().equals(attacker.getUsername());
        GameActionService service = service(defaultCombat(), resolver(target), inCombat);

        GameActionResult result = service.initiatePlayerDuel(attacker, "target");

        assertEquals("You cannot start a duel while in combat.", result.messages().getFirst().text());
    }

    @Test
    void initiateDuelRejectedWhenTargetInMobCombat() {
        Predicate<Player> inCombat = p -> p.getUsername().equals(target.getUsername());
        GameActionService service = service(defaultCombat(), resolver(target), inCombat);

        GameActionResult result = service.initiatePlayerDuel(attacker, "target");

        assertEquals("target is already in combat.", result.messages().getFirst().text());
    }

    @Test
    void initiateDuelRejectedWhenTargetAlreadyDueling() {
        duelService.activate(Username.of("someone"), target.getUsername());
        GameActionService service = service(defaultCombat(), resolver(target), _ -> false);

        GameActionResult result = service.initiatePlayerDuel(attacker, "target");

        assertEquals("target is already in combat.", result.messages().getFirst().text());
    }

    // ── accept ───────────────────────────────────────────────────────────────

    @Test
    void acceptDuelEngagesBothPlayers() {
        GameActionService service = service(defaultCombat(), resolver(target), _ -> false);
        service.initiatePlayerDuel(attacker, "target");

        GameActionResult result = service.acceptPlayerDuel(target);

        assertTrue(result.messages().stream().anyMatch(m ->
            m.type() == GameMessage.Type.SOURCE && m.text().equals("You accept attacker's duel. Combat begins!")));
        assertTrue(result.messages().stream().anyMatch(m ->
            m.type() == GameMessage.Type.PLAYER && m.text().equals("target accepts your duel. Combat begins!")));
        assertTrue(duelService.isDueling(attacker.getUsername()));
        assertTrue(duelService.isDueling(target.getUsername()));
        assertTrue(duelService.areDueling(attacker.getUsername(), target.getUsername()));
    }

    @Test
    void acceptDuelWithoutPendingChallengeFails() {
        GameActionService service = service(defaultCombat(), resolver(target), _ -> false);

        GameActionResult result = service.acceptPlayerDuel(target);

        assertEquals("You have no pending duel challenge.", result.messages().getFirst().text());
    }

    // ── timeout ──────────────────────────────────────────────────────────────

    @Test
    void pendingDuelExpiresSilentlyAfterTimeoutTicks() {
        duelService.requestDuel(attacker.getUsername(), target.getUsername());

        for (int i = 0; i < DuelService.DEFAULT_TIMEOUT_TICKS; i++) {
            assertTrue(duelService.pendingChallenger(target.getUsername()).isPresent(),
                "challenge should still be pending before the timeout elapses");
            duelService.tick();
        }

        assertTrue(duelService.pendingChallenger(target.getUsername()).isEmpty());
        assertTrue(duelService.stateOf(target.getUsername()).isEmpty());
    }

    // ── duel resolution ────────────────────────────────────────────────────────

    @Test
    void duelDeathSuppressesCorpseGoldXpAndLeavesLoserNearDeath() {
        Player lowHpTarget = playerWithHp("target", 2);
        duelService.activate(attacker.getUsername(), lowHpTarget.getUsername());
        GameActionService service = service(defaultCombat(), resolver(lowHpTarget), _ -> false);

        GameActionResult result = service.attack(attacker, "target");

        Player loser = result.updatedTarget();
        assertEquals(1, loser.getVitals().hp(), "loser is left at near-death, not slain");
        assertFalse(loser.isDead());
        assertTrue(result.messages().stream().anyMatch(m -> m.text().equals("Duel ended.")));
        // No corpse spawned and the loser was not sent to respawn (location retained).
        assertTrue(roomService.findPlayerLocation(lowHpTarget.getUsername()).isPresent());
        assertFalse(roomHasCorpse());
        // Duel is over for both participants.
        assertFalse(duelService.isDueling(attacker.getUsername()));
        assertFalse(duelService.isDueling(lowHpTarget.getUsername()));
    }

    @Test
    void duelResolutionRecordsWinForVictorAndLossForLoser() {
        Player lowHpTarget = playerWithHp("target", 2);
        duelService.activate(attacker.getUsername(), lowHpTarget.getUsername());
        GameActionService service = service(defaultCombat(), resolver(lowHpTarget), _ -> false);

        GameActionResult result = service.attack(attacker, "target");

        assertEquals(1, result.updatedSource().getDuelWins(), "victor gains a duel win");
        assertEquals(0, result.updatedSource().getDuelLosses(), "victor gains no loss");
        assertEquals(1, result.updatedTarget().getDuelLosses(), "loser gains a duel loss");
        assertEquals(0, result.updatedTarget().getDuelWins(), "loser gains no win");
    }

    @Test
    void endPlayerDuelIncrementsExistingRecordsOnBothSides() {
        Player survivor = attacker.withDuelWins(2).withDuelLosses(1);
        Player loser = playerWithHp("target", 0).withDuelWins(0).withDuelLosses(4);
        duelService.activate(survivor.getUsername(), loser.getUsername());
        GameActionService service = service(defaultCombat(), resolver(loser), _ -> false);

        GameActionResult result = service.endPlayerDuel(survivor, loser);

        assertEquals(3, result.updatedSource().getDuelWins());
        assertEquals(1, result.updatedSource().getDuelLosses());
        assertEquals(0, result.updatedTarget().getDuelWins());
        assertEquals(5, result.updatedTarget().getDuelLosses());
    }

    @Test
    void forfeitViaClearForDoesNotAffectDuelRecords() {
        duelService.activate(attacker.getUsername(), target.getUsername());

        duelService.clearFor(attacker.getUsername());

        assertEquals(0, attacker.getDuelWins());
        assertEquals(0, attacker.getDuelLosses());
        assertEquals(0, target.getDuelWins());
        assertEquals(0, target.getDuelLosses());
    }

    @Test
    void nonDuelPvpDeathStillSpawnsCorpseAndClearsLocation() {
        Player lowHpTarget = playerWithHp("target", 2);
        GameActionService service = service(defaultCombat(), resolver(lowHpTarget), _ -> false);

        GameActionResult result = service.attack(attacker, "target");

        Player loser = result.updatedTarget();
        assertTrue(loser.isDead());
        assertEquals(0, loser.getVitals().hp());
        assertTrue(roomService.findPlayerLocation(lowHpTarget.getUsername()).isEmpty());
        assertTrue(roomHasCorpse());
    }

    // ── flee / room move ────────────────────────────────────────────────────────

    @Test
    void fleeIsBlockedDuringDuel() {
        duelService.activate(attacker.getUsername(), target.getUsername());
        GameActionService service = service(defaultCombat(), resolver(target), _ -> true);
        Room room = roomService.look(attacker.getUsername()).room();

        FleeResult result = service.flee(attacker, room);

        assertFalse(result.fled());
        assertEquals("You cannot flee from a duel!", result.message());
    }

    @Test
    void clearForCancelsDuelOnRoomMoveOrDisconnect() {
        duelService.activate(attacker.getUsername(), target.getUsername());

        duelService.clearFor(attacker.getUsername());

        assertFalse(duelService.isDueling(attacker.getUsername()));
        assertFalse(duelService.isDueling(target.getUsername()));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private boolean roomHasCorpse() {
        // The attacker never leaves ROOM_A, so look through their eyes to inspect the ground.
        return roomService.look(attacker.getUsername()).lines().stream()
            .anyMatch(line -> line.toLowerCase(Locale.ROOT).contains("corpse"));
    }

    private CombatEngine defaultCombat() {
        AttackId defaultAttack = CombatSettings.defaultAttackId();
        AttackDefinition attack = new AttackDefinition(defaultAttack, "punch", 2, 4, 0, 0, 0, List.of());
        return new CombatEngine(
            new StubAttackRepository(Map.of(defaultAttack, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            new FixedCombatRandom(10, 3, 100)
        );
    }

    private GameActionService service(
        CombatEngine combatEngine, AbilityTargetResolver resolver, Predicate<Player> inCombat) {
        AbilityRegistry abilityRegistry = new AbilityRegistry(List.of());
        AbilityCostResolver costResolver = new BasicAbilityCostResolver();
        EffectEngine effectEngine = new EffectEngine(new StubEffectRepository(Map.of()));
        GameActionService service = new GameActionService(
            abilityRegistry, costResolver, effectEngine, combatEngine, roomService,
            resolver, new TestCooldowns(), testEncumbranceService(), inCombat);
        service.setDuelService(duelService);
        return service;
    }

    private AbilityTargetResolver resolver(Player... players) {
        return (source, input) -> {
            for (Player p : players) {
                if (p.getUsername().getValue().equalsIgnoreCase(input)) {
                    return Optional.of(p);
                }
            }
            return Optional.empty();
        };
    }

    private Player player(String username) {
        return Player.of(User.of(Username.of(username), Password.hash("pw", 1000)), "prompt", false);
    }

    private Player playerWithHp(String username, int hp) {
        PlayerVitals vitals = new PlayerVitals(hp, 20, 20, 20, 20, 20);
        return new Player(
            User.of(Username.of(username), Password.hash("pw", 1000)),
            1, 0, vitals, List.of(), "prompt", false,
            List.of(), null, null
        );
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
        private final Map<AbilityId, Integer> cooldowns = new HashMap<>();

        @Override
        public boolean isOnCooldown(AbilityId abilityId) {
            Integer remaining = cooldowns.get(abilityId);
            return remaining != null && remaining > 0;
        }

        @Override
        public int remainingTicks(AbilityId abilityId) {
            return cooldowns.getOrDefault(abilityId, 0);
        }

        @Override
        public void startCooldown(AbilityId abilityId, int ticks) {
            cooldowns.put(abilityId, ticks);
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

    private static class FixedCombatRandom implements CombatRandom {
        private final int[] rolls;
        private int index;

        FixedCombatRandom(int... rolls) {
            this.rolls = rolls;
        }

        @Override
        public int roll(int minInclusive, int maxInclusive) {
            if (index >= rolls.length) {
                return rolls[rolls.length - 1];
            }
            return rolls[index++];
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
