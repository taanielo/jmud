package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityCooldown;
import io.taanielo.jmud.core.ability.AbilityCost;
import io.taanielo.jmud.core.ability.AbilityDefinition;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityTargeting;
import io.taanielo.jmud.core.ability.AbilityType;
import io.taanielo.jmud.core.action.GameActionResult;
import io.taanielo.jmud.core.action.PlayerEventBus;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatRandom;
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
 * Unit tests for the necromancer-style SUMMON spell in {@link MobRegistry}: summon validation
 * ({@link MobRegistry#processSummon}), pet spawning, lifetime decay/auto-dismissal, combat
 * participation against room hostiles, and dismissal — all without networking (AGENTS.md §10).
 */
class MobRegistrySummonTest {

    private static final RoomId ROOM_A = RoomId.of("room.a");
    private static final RoomId ROOM_B = RoomId.of("room.b");
    private static final AttackId PUNCH = AttackId.of("attack.punch");
    private static final MobId PET_ID = MobId.of("spectral-servant");
    private static final MobId GOBLIN_ID = MobId.of("mob.goblin");

    /** A deterministic 5-damage attack shared by the pet and its foes. */
    private static final AttackDefinition PUNCH_ATTACK =
        new AttackDefinition(PUNCH, "punch", 5, 5, 0, 0, 0, List.of());

    /** summon: level 1, 8 mana, 10-tick cooldown, NONE targeting (command-driven). */
    private static final Ability SUMMON = summonSpell(1);

    private static Ability summonSpell(int level) {
        return new AbilityDefinition(
            AbilityId.of("spell.summon"),
            "summon",
            AbilityType.SPELL,
            level,
            new AbilityCost(8, 0),
            new AbilityCooldown(10),
            AbilityTargeting.NONE,
            List.of(),
            List.of(),
            List.of());
    }

    private Player mage(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private MobTemplate petTemplate(int durationTicks) {
        return new MobTemplate(
            PET_ID,
            "Spectral Servant",
            25,
            PUNCH,
            null,
            false,
            List.of(),
            ROOM_A,
            1,
            0,
            0,
            null,
            List.of("undead", "pet"),
            false,
            null,
            durationTicks);
    }

    private MobTemplate goblinTemplate(int hp, RoomId room, int xpReward) {
        return new MobTemplate(
            GOBLIN_ID,
            "Goblin",
            hp,
            PUNCH,
            null,
            false,
            List.of(),
            room,
            1,
            10,
            xpReward,
            null,
            List.of(),
            false);
    }

    private MobRegistry buildRegistry(
        Player caster, List<Username> eventSink, List<GameActionResult> results, MobTemplate... templates) {
        return buildRegistry(caster, MobRegistryTestSupport.random(), eventSink, results, templates);
    }

    private MobRegistry buildRegistry(
        Player caster, CombatRandom random, List<Username> eventSink, List<GameActionResult> results,
        MobTemplate... templates) {
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(templates));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(PUNCH, PUNCH_ATTACK));
        ItemRepository itemRepo = new StubItemRepository();

        RoomService roomService = new RoomService(new StubRoomRepository(), ROOM_A);
        roomService.ensurePlayerLocation(caster.getUsername());

        StubPlayerRepository playerRepo = new StubPlayerRepository(caster);
        PlayerEventBus bus = new PlayerEventBus();
        bus.register(caster.getUsername(), result -> {
            eventSink.add(caster.getUsername());
            results.add(result);
        });

        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus, random);
        registry.init();
        return registry;
    }

    private static boolean containsText(List<GameActionResult> results, String fragment) {
        return results.stream()
            .flatMap(r -> r.messages().stream())
            .anyMatch(m -> m.text().contains(fragment));
    }

    private static boolean containsText(GameActionResult result, String fragment) {
        return result.messages().stream().anyMatch(m -> m.text().contains(fragment));
    }

    private long summonedMobsInRoom(MobRegistry registry, RoomId roomId) {
        return registry.getMobsInRoom(roomId).stream().filter(MobInstance::isSummoned).count();
    }

    @Test
    void summon_spawnsPetInCasterRoom_andDeductsMana() {
        Player caster = mage("necro");
        MobRegistry registry = buildRegistry(caster, new ArrayList<>(), new ArrayList<>(),
            petTemplate(20));

        GameActionResult result = registry.processSummon(caster, SUMMON, ROOM_A);

        assertNotNull(result.updatedSource(), "A successful summon returns the mana-deducted caster");
        assertTrue(result.updatedSource().getVitals().getMana() < caster.getVitals().getMana(),
            "Summoning should deduct mana");
        assertTrue(containsText(result, "rises to fight at your side"),
            "The caster should be told the pet was summoned");
        assertTrue(summonedMobsInRoom(registry, ROOM_A) == 1,
            "A summoned pet should now stand in the caster's room");
    }

    @Test
    void summon_rejectedWhenInsufficientMana() {
        Player caster = mage("necro");
        caster = caster.withVitals(caster.getVitals().consumeMana(15)); // 20 - 15 = 5, cost is 8
        MobRegistry registry = buildRegistry(caster, new ArrayList<>(), new ArrayList<>(),
            petTemplate(20));

        GameActionResult result = registry.processSummon(caster, SUMMON, ROOM_A);

        assertTrue(containsText(result, "lack the mana"), "A caster who cannot pay is rejected");
        assertNull(result.updatedSource(), "A rejected summon must not deduct mana");
        assertTrue(summonedMobsInRoom(registry, ROOM_A) == 0, "No pet should be spawned");
    }

    @Test
    void summon_rejectedWhenLevelTooLow() {
        Player caster = mage("apprentice"); // level 1
        MobRegistry registry = buildRegistry(caster, new ArrayList<>(), new ArrayList<>(),
            petTemplate(20));

        GameActionResult result = registry.processSummon(caster, summonSpell(5), ROOM_A);

        assertTrue(containsText(result, "not experienced enough"), "A too-low-level caster is rejected");
        assertNull(result.updatedSource(), "A rejected summon must not deduct mana");
        assertTrue(summonedMobsInRoom(registry, ROOM_A) == 0, "No pet should be spawned");
    }

    @Test
    void summon_onlyOnePetAllowedAtATime() {
        Player caster = mage("necro");
        MobRegistry registry = buildRegistry(caster, new ArrayList<>(), new ArrayList<>(),
            petTemplate(20));

        GameActionResult first = registry.processSummon(caster, SUMMON, ROOM_A);
        assertNotNull(first.updatedSource(), "First summon succeeds");
        GameActionResult second = registry.processSummon(caster, SUMMON, ROOM_A);

        assertTrue(containsText(second, "already have a summoned pet"), "A second summon is rejected");
        assertNull(second.updatedSource(), "The rejected second summon must not deduct mana");
        assertTrue(summonedMobsInRoom(registry, ROOM_A) == 1, "Only one pet may exist");
    }

    @Test
    void pet_autoDismissesWhenLifetimeElapses() {
        Player caster = mage("necro");
        List<GameActionResult> results = new ArrayList<>();
        // Goblin lives in ROOM_B so the pet has no foe and simply decays.
        MobRegistry registry = buildRegistry(caster, new ArrayList<>(), results,
            petTemplate(3), goblinTemplate(20, ROOM_B, 5));

        registry.processSummon(caster, SUMMON, ROOM_A);
        assertTrue(summonedMobsInRoom(registry, ROOM_A) == 1, "Pet is present after summoning");

        registry.tick(); // 3 -> 2
        registry.tick(); // 2 -> 1
        assertTrue(summonedMobsInRoom(registry, ROOM_A) == 1, "Pet still alive before its lifetime elapses");
        registry.tick(); // 1 -> 0, expires

        assertTrue(summonedMobsInRoom(registry, ROOM_A) == 0, "Pet auto-dismisses when its lifetime elapses");
        assertTrue(containsText(results, "fades back into the ether"),
            "The summoner should be told the pet faded away");
    }

    @Test
    void pet_attacksHostileMobInRoomEachTick() {
        Player caster = mage("necro");
        List<GameActionResult> results = new ArrayList<>();
        MobRegistry registry = buildRegistry(caster, new ArrayList<>(), results,
            petTemplate(20), goblinTemplate(20, ROOM_A, 5));

        registry.processSummon(caster, SUMMON, ROOM_A);
        registry.tick();

        assertTrue(containsText(results, "Your Spectral Servant strikes the Goblin"),
            "The pet should strike the hostile mob and report to its summoner");
        MobInstance goblin = registry.getMobsInRoom(ROOM_A).stream()
            .filter(m -> !m.isSummoned())
            .findFirst()
            .orElseThrow();
        assertTrue(goblin.currentHp() < 20, "The struck goblin should have taken damage");
    }

    @Test
    void pet_killAwardsExperienceToSummoner() {
        Player caster = mage("necro");
        List<GameActionResult> results = new ArrayList<>();
        // 5-HP goblin dies to the pet's 5-damage strike in a single tick.
        MobRegistry registry = buildRegistry(caster, new ArrayList<>(), results,
            petTemplate(20), goblinTemplate(5, ROOM_A, 5));

        registry.processSummon(caster, SUMMON, ROOM_A);
        registry.tick();

        assertTrue(containsText(results, "You slay the Goblin"),
            "A pet that lands the killing blow credits the summoner with the kill");
        assertTrue(containsText(results, "experience points"),
            "The summoner should gain the full experience reward");
        assertTrue(results.stream().anyMatch(r ->
                r.updatedSource() != null && r.updatedSource().getTotalKills() >= 1),
            "The kill should count toward the summoner's total kills");
    }

    @Test
    void dismiss_removesActivePet() {
        Player caster = mage("necro");
        MobRegistry registry = buildRegistry(caster, new ArrayList<>(), new ArrayList<>(),
            petTemplate(20));

        registry.processSummon(caster, SUMMON, ROOM_A);
        assertTrue(summonedMobsInRoom(registry, ROOM_A) == 1, "Pet present before dismissal");

        GameActionResult result = registry.dismissPet(caster, ROOM_A);

        assertTrue(containsText(result, "fades away"), "Dismissing tells the caster the pet is gone");
        assertTrue(summonedMobsInRoom(registry, ROOM_A) == 0, "The pet is removed on dismissal");
    }

    @Test
    void dismiss_withNoPetReturnsError() {
        Player caster = mage("necro");
        MobRegistry registry = buildRegistry(caster, new ArrayList<>(), new ArrayList<>(),
            petTemplate(20));

        GameActionResult result = registry.dismissPet(caster, ROOM_A);

        assertTrue(containsText(result, "no summoned pet"), "Dismissing without a pet is rejected");
        assertFalse(result.messages().isEmpty(), "An explanatory message is returned");
    }

    @Test
    void pet_canMissItsFoe_dealingNoDamage() {
        Player caster = mage("necro");
        List<GameActionResult> results = new ArrayList<>();
        // Pet hit roll 80 (> 75) misses; foe retaliation hit roll 80 (> 75) also misses.
        MobRegistry registry = buildRegistry(caster, new ScriptedRandom(80, 80),
            new ArrayList<>(), results, petTemplate(20), goblinTemplate(20, ROOM_A, 5));

        registry.processSummon(caster, SUMMON, ROOM_A);
        registry.tick();

        assertTrue(containsText(results, "Your Spectral Servant lunges at the Goblin but misses"),
            "A pet whose swing misses should report a miss, not a strike");
        MobInstance goblin = registry.getMobsInRoom(ROOM_A).stream()
            .filter(m -> !m.isSummoned())
            .findFirst()
            .orElseThrow();
        assertTrue(goblin.currentHp() == 20, "A missed foe takes no damage");
    }

    @Test
    void pet_canCritItsFoe_forBonusDamage() {
        Player caster = mage("necro");
        List<GameActionResult> results = new ArrayList<>();
        // Pet hit 10 (lands), crit 1 (crits): 5 damage becomes 10. Foe retaliation hit 80 misses.
        MobRegistry registry = buildRegistry(caster, new ScriptedRandom(10, 1, 80),
            new ArrayList<>(), results, petTemplate(20), goblinTemplate(20, ROOM_A, 5));

        registry.processSummon(caster, SUMMON, ROOM_A);
        registry.tick();

        assertTrue(containsText(results, "A critical hit! Your Spectral Servant strikes the Goblin"),
            "A pet crit should read distinctly from a normal strike");
        MobInstance goblin = registry.getMobsInRoom(ROOM_A).stream()
            .filter(m -> !m.isSummoned())
            .findFirst()
            .orElseThrow();
        assertTrue(goblin.currentHp() == 10, "A pet crit should deal double (5 * 2 = 10) damage");
    }

    @Test
    void foeRetaliation_againstPet_canCrit() {
        Player caster = mage("necro");
        List<GameActionResult> results = new ArrayList<>();
        // Pet hit 80 misses; foe retaliation hit 10 (lands), crit 1 (crits): 5 damage becomes 10.
        MobRegistry registry = buildRegistry(caster, new ScriptedRandom(80, 10, 1),
            new ArrayList<>(), results, petTemplate(20), goblinTemplate(20, ROOM_A, 5));

        registry.processSummon(caster, SUMMON, ROOM_A);
        registry.tick();

        assertTrue(containsText(results,
                "A critical hit! The Goblin retaliates against your Spectral Servant"),
            "The foe's retaliation against the pet can crit for symmetry with the pet's own swing");
        assertTrue(containsText(results, "15 HP remaining"),
            "A 10-damage crit should leave the 25-HP pet at 15 HP");
    }

    // ── stubs ─────────────────────────────────────────────────────────

    /**
     * A {@link CombatRandom} returning a fixed sequence of rolls (each clamped into the requested
     * range) so pet hit/crit outcomes are fully deterministic. {@link #nextDouble()} returns
     * {@code 1.0} so loot/wander probability gates never consume a scripted roll.
     */
    private static final class ScriptedRandom implements CombatRandom {
        private final Deque<Integer> rolls = new ArrayDeque<>();

        ScriptedRandom(int... values) {
            for (int value : values) {
                rolls.add(value);
            }
        }

        @Override
        public int roll(int minInclusive, int maxInclusive) {
            Integer next = rolls.poll();
            int value = next == null ? minInclusive : next;
            return Math.max(minInclusive, Math.min(maxInclusive, value));
        }

        @Override
        public double nextDouble() {
            return 1.0;
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
        private final Map<RoomId, Room> rooms = Map.of(
            ROOM_A, new Room(
                ROOM_A, "Room A", "A clearing.", Map.of(Direction.NORTH, ROOM_B), List.of(), List.of()),
            ROOM_B, new Room(
                ROOM_B, "Room B", "A thicket.", Map.of(Direction.SOUTH, ROOM_A), List.of(), List.of())
        );

        @Override
        public void save(Room room) throws RepositoryException {
        }

        @Override
        public Optional<Room> findById(RoomId id) throws RepositoryException {
            return Optional.ofNullable(rooms.get(id));
        }
    }
}
