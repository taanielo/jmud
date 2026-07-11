package io.taanielo.jmud.core.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityCooldown;
import io.taanielo.jmud.core.ability.AbilityCooldownTracker;
import io.taanielo.jmud.core.ability.AbilityCost;
import io.taanielo.jmud.core.ability.AbilityCostResolver;
import io.taanielo.jmud.core.ability.AbilityDefinition;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityRegistry;
import io.taanielo.jmud.core.ability.AbilityTargetResolver;
import io.taanielo.jmud.core.ability.AbilityTargeting;
import io.taanielo.jmud.core.ability.AbilityType;
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
import io.taanielo.jmud.core.party.PartyService;
import io.taanielo.jmud.core.player.EncumbranceService;
import io.taanielo.jmud.core.player.OnlinePlayerLookup;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.world.Corpse;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Unit tests for the Cleric RESURRECTION spell handled by {@link GameActionService#resurrect}.
 */
class GameActionServiceResurrectionTest {

    private static final AbilityId RESURRECTION = AbilityId.of("spell.resurrection");
    private static final RoomId CASTER_ROOM = RoomId.of("chapel");
    private static final RoomId DEATH_ROOM = RoomId.of("crypt");

    private RoomService roomService;
    private PartyService partyService;
    private Map<Username, Player> online;
    private TestCooldowns cooldowns;

    private Player caster;
    private Player deadTarget;

    @BeforeEach
    void setUp() {
        Room chapel = new Room(
            CASTER_ROOM, "Chapel", "A quiet chapel.",
            Map.of(Direction.NORTH, DEATH_ROOM), List.of(), List.of());
        Room crypt = new Room(
            DEATH_ROOM, "Crypt", "A cold crypt.",
            Map.of(Direction.SOUTH, CASTER_ROOM), List.of(), List.of());
        roomService = new RoomService(
            new TestRoomRepository(Map.of(CASTER_ROOM, chapel, DEATH_ROOM, crypt)), CASTER_ROOM);
        partyService = new PartyService();
        online = new HashMap<>();
        cooldowns = new TestCooldowns();

        caster = clericCaster("cleric", 40);
        roomService.movePlayerTo(caster.getUsername(), CASTER_ROOM);
        register(caster);

        // Simulate a death in the crypt: 50 gold dropped into a tracked corpse, player marked dead.
        Player living = player("faller", 20);
        roomService.movePlayerTo(living.getUsername(), DEATH_ROOM);
        roomService.spawnCorpse(living.getUsername(), DEATH_ROOM, 50);
        deadTarget = living.withGold(0).die();
        roomService.clearPlayerLocation(deadTarget.getUsername());
        register(deadTarget);
    }

    // ── success ────────────────────────────────────────────────────────────────

    @Test
    void resurrectRevivesPartyMemberInCasterRoomWithRefundAndRemovesCorpse() {
        formParty();
        GameActionService service = service();

        GameActionResult result = service.resurrect(caster, "faller");

        Player revived = result.updatedTarget();
        assertNotNull(revived);
        assertFalse(revived.isDead());
        assertEquals(10, revived.getVitals().hp(), "revived at half of 20 max HP");
        assertEquals(50, revived.getGold(), "corpse gold refunded");
        assertEquals(
            Optional.of(CASTER_ROOM),
            roomService.findPlayerLocation(revived.getUsername()),
            "revived in caster's room");
        assertTrue(roomService.findCorpseByOwner("faller").isEmpty(), "corpse consumed");

        Player updatedCaster = result.updatedSource();
        assertNotNull(updatedCaster);
        assertEquals(10, updatedCaster.getVitals().mana(), "30 mana spent from 40");

        assertTrue(result.messages().stream().anyMatch(m ->
            m.type() == GameMessage.Type.SOURCE
                && m.text().equals("You call upon the light to restore faller to life.")));
        assertTrue(result.messages().stream().anyMatch(m ->
            m.type() == GameMessage.Type.PLAYER && m.text().contains("life floods back")));
        assertTrue(result.messages().stream().anyMatch(m ->
            m.type() == GameMessage.Type.ROOM));
    }

    // ── failures ─────────────────────────────────────────────────────────────

    @Test
    void resurrectFailsWhenCorpseAlreadyDecayed() {
        formParty();
        // Decay: remove the tracked corpse, as CorpseDecayTicker does after the decay window.
        Corpse corpse = roomService.findCorpseByOwner("faller").orElseThrow();
        roomService.removeCorpse(corpse);
        GameActionService service = service();

        GameActionResult result = service.resurrect(caster, "faller");

        assertNull(result.updatedSource(), "no mana spent on failure");
        assertTrue(result.messages().getFirst().text().contains("decayed"));
        assertEquals(40, caster.getVitals().mana());
    }

    @Test
    void resurrectFailsWhenTargetNotInParty() {
        GameActionService service = service();

        GameActionResult result = service.resurrect(caster, "faller");

        assertNull(result.updatedSource());
        assertEquals("faller is not in your party.", result.messages().getFirst().text());
    }

    @Test
    void resurrectFailsWhenTargetAlive() {
        formParty();
        Player alive = player("faller", 20);
        roomService.movePlayerTo(alive.getUsername(), DEATH_ROOM);
        register(alive);
        GameActionService service = service();

        GameActionResult result = service.resurrect(caster, "faller");

        assertNull(result.updatedSource());
        assertEquals("faller is not dead.", result.messages().getFirst().text());
    }

    @Test
    void resurrectFailsWhenOnCooldown() {
        formParty();
        cooldowns.startCooldown(RESURRECTION, 12);
        GameActionService service = service();

        GameActionResult result = service.resurrect(caster, "faller");

        assertNull(result.updatedSource());
        assertTrue(result.messages().getFirst().text().contains("12 ticks remaining"));
        // Target remains dead and corpse intact.
        assertTrue(roomService.findCorpseByOwner("faller").isPresent());
    }

    @Test
    void resurrectFailsWhenOutOfMana() {
        formParty();
        caster = clericCaster("cleric", 5);
        register(caster);
        GameActionService service = service();

        GameActionResult result = service.resurrect(caster, "faller");

        assertNull(result.updatedSource());
        assertEquals("You do not have enough mana to cast resurrection.", result.messages().getFirst().text());
        assertTrue(roomService.findCorpseByOwner("faller").isPresent());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void formParty() {
        partyService.form(caster.getUsername());
        partyService.invite(caster.getUsername(), deadTarget.getUsername(), true);
        partyService.accept(deadTarget.getUsername());
    }

    private void register(Player player) {
        online.put(player.getUsername(), player);
    }

    private GameActionService service() {
        Ability resurrection = new AbilityDefinition(
            RESURRECTION, "resurrection", AbilityType.SPELL, 1,
            new AbilityCost(30, 0), new AbilityCooldown(30), AbilityTargeting.NONE,
            List.of(), List.of(), List.of());
        AbilityRegistry registry = new AbilityRegistry(List.of(resurrection));
        AbilityCostResolver costResolver = new BasicAbilityCostResolver();
        EffectEngine effectEngine = new EffectEngine(new StubEffectRepository(Map.of()));
        AbilityTargetResolver resolver = (source, input) -> Optional.empty();
        GameActionService service = new GameActionService(
            registry, costResolver, effectEngine, defaultCombat(), roomService,
            resolver, cooldowns, testEncumbranceService(), _ -> false);
        service.setPartyService(partyService);
        OnlinePlayerLookup lookup = username -> Optional.ofNullable(online.get(username));
        service.setOnlinePlayerLookup(lookup);
        return service;
    }

    private CombatEngine defaultCombat() {
        AttackId defaultAttack = CombatSettings.defaultAttackId();
        AttackDefinition attack = new AttackDefinition(defaultAttack, "punch", 2, 4, 0, 0, 0, List.of());
        return new CombatEngine(
            new StubAttackRepository(Map.of(defaultAttack, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            new ThreadLocalCombatRandom());
    }

    private Player clericCaster(String username, int mana) {
        PlayerVitals vitals = new PlayerVitals(30, 30, mana, 40, 30, 30);
        return new Player(
            User.of(Username.of(username), Password.hash("pw", 1000)),
            1, 0, vitals, List.of(), "prompt", false,
            List.of(RESURRECTION), null, null);
    }

    private Player player(String username, int maxHp) {
        PlayerVitals vitals = new PlayerVitals(maxHp, maxHp, 20, 20, 20, 20);
        return new Player(
            User.of(Username.of(username), Password.hash("pw", 1000)),
            1, 0, vitals, List.of(), "prompt", false,
            List.of(), null, null);
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
