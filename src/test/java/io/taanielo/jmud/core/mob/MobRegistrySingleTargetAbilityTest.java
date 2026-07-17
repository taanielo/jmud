package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
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
import io.taanielo.jmud.core.ability.AbilityEffect;
import io.taanielo.jmud.core.ability.AbilityEffectKind;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityOperation;
import io.taanielo.jmud.core.ability.AbilityStat;
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
import io.taanielo.jmud.core.combat.CombatSettings;
import DamageType;
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
 * Unit tests for harmful single-target spell/skill resolution against a mob in
 * {@link MobRegistry#processPlayerSingleTargetAbility} (issue #651): {@code CAST <spell> <mob>} /
 * {@code USE <skill> <mob>} strike a monster, rolling hit/crit through the same shared resolution as
 * melee/ranged/AoE, awarding kill rewards on a killing blow, gating {@code HARMFUL_UNDEAD} on the
 * mob's {@code undead} tag, and preserving the {@code HARMFUL_OPENER} stealth bonus and opener gate.
 *
 * <p>These exercise the real production mob path — the registry's own hit resolution and kill-reward
 * pipeline — rather than an {@code AbilityEngine} with a hand-fed target resolver, which is the
 * coverage gap that let single-target mob-targeting ship broken. All without networking (AGENTS.md
 * §10).
 */
class MobRegistrySingleTargetAbilityTest {

    private static final RoomId ROOM_A = RoomId.of("room.a");
    private static final RoomId ROOM_B = RoomId.of("room.b");

    private static final AttackId UNARMED = AttackId.of(CombatSettings.DEFAULT_ATTACK_ID);
    private static final AttackDefinition UNARMED_MELEE =
        new AttackDefinition(UNARMED, "punch", 1, 1, 0, 0, 0, List.of());

    /** fireball: HARMFUL single-target spell, 4 mana, 5 damage. */
    private static final Ability FIREBALL = new AbilityDefinition(
        AbilityId.of("spell.fireball"),
        "fireball",
        AbilityType.SPELL,
        1,
        new AbilityCost(4, 0),
        new AbilityCooldown(3),
        AbilityTargeting.HARMFUL,
        List.of(),
        List.of(new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 5, null)),
        List.of()
    );

    /** searing-light: HARMFUL_UNDEAD spell, 4 mana, 5 holy damage (only affects undead-tagged mobs). */
    private static final Ability SEARING_LIGHT = new AbilityDefinition(
        AbilityId.of("spell.searing-light"),
        "searing-light",
        AbilityType.SPELL,
        1,
        new AbilityCost(4, 0),
        new AbilityCooldown(3),
        AbilityTargeting.HARMFUL_UNDEAD,
        List.of(),
        List.of(new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 5, null)),
        List.of()
    );

    /** backstab: HARMFUL_OPENER skill, 3 move, 5 damage; gains a flat stealth bonus when opened hidden. */
    private static final Ability BACKSTAB = new AbilityDefinition(
        AbilityId.of("skill.backstab"),
        "backstab",
        AbilityType.SKILL,
        1,
        new AbilityCost(0, 3),
        new AbilityCooldown(3),
        AbilityTargeting.HARMFUL_OPENER,
        List.of(),
        List.of(new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 5, null)),
        List.of()
    );

    /** fire-bolt: FIRE-typed HARMFUL spell, 4 mana, 10 fire damage. */
    private static final Ability FIRE_BOLT = new AbilityDefinition(
        AbilityId.of("spell.fire-bolt"),
        "fire-bolt",
        AbilityType.SPELL,
        1,
        new AbilityCost(4, 0),
        new AbilityCooldown(3),
        AbilityTargeting.HARMFUL,
        List.of(),
        List.of(new AbilityEffect(
            AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 10, null, "FIRE")),
        List.of()
    );

    private Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private MobRegistry buildRegistry(
        Player caster, int mobHp, RoomId mobRoom, List<String> tags, CombatRandom random,
        Map<DamageType, Integer> resistances,
        Map<DamageType, Integer> vulnerabilities) {
        MobTemplate template = new MobTemplate(
            MobId.of("mob.goblin"),
            "Goblin",
            mobHp,
            UNARMED,
            null,
            false,
            List.of(),
            mobRoom,
            1,
            10,
            5,
            null,
            tags,
            false,
            null,
            null,
            false,
            null,
            null,
            false,
            false,
            0,
            resistances,
            vulnerabilities
        );
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(UNARMED, UNARMED_MELEE));
        ItemRepository itemRepo = new StubItemRepository();

        RoomService roomService = new RoomService(new StubRoomRepository(), ROOM_A);
        roomService.ensurePlayerLocation(caster.getUsername());

        StubPlayerRepository playerRepo = new StubPlayerRepository(caster);
        PlayerEventBus bus = new PlayerEventBus();

        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus, random);
        registry.init();
        return registry;
    }

    @Test
    void typedSpell_onResistantMob_dealsReducedDamageWithQualifier() {
        Player caster = player("merlin");
        // Hit roll 10 (lands), crit roll 100 (no crit): a clean 10-damage fire hit.
        MobRegistry registry = buildRegistry(caster, 100, ROOM_A, List.of(), new ScriptedRandom(10, 100),
            Map.of(DamageType.FIRE, 50), Map.of());

        GameActionResult result =
            registry.processPlayerSingleTargetAbility(caster, FIRE_BOLT, "goblin", ROOM_A);

        // 50% fire resistance halves the 10 damage to 5.
        assertEquals(95, onlyMob(registry).currentHp(),
            "A 50% fire-resistant mob should take half of the fire spell's damage");
        assertTrue(containsText(result, "for 5 damage"), "The strike should report the reduced damage");
        assertTrue(containsText(result, "sizzle weakly"),
            "A resisted fire hit should carry a resist qualifier so the matchup is legible");
    }

    @Test
    void typedSpell_onVulnerableMob_dealsIncreasedDamageWithQualifier() {
        Player caster = player("merlin");
        MobRegistry registry = buildRegistry(caster, 100, ROOM_A, List.of(), new ScriptedRandom(10, 100),
            Map.of(), Map.of(DamageType.FIRE, 50));

        GameActionResult result =
            registry.processPlayerSingleTargetAbility(caster, FIRE_BOLT, "goblin", ROOM_A);

        // 50% fire vulnerability raises the 10 damage to 15.
        assertEquals(85, onlyMob(registry).currentHp(),
            "A fire-vulnerable mob should take amplified fire damage");
        assertTrue(containsText(result, "for 15 damage"), "The strike should report the amplified damage");
        assertTrue(containsText(result, "roar hungrily"),
            "A vulnerable fire hit should carry a vulnerability qualifier");
    }

    @Test
    void untypedSpell_isUnaffectedByMobResistanceOrVulnerability() {
        Player caster = player("merlin");
        // FIREBALL is untyped (physical); a fire resistance/vulnerability entry must not touch it.
        MobRegistry registry = buildRegistry(caster, 100, ROOM_A, List.of(), new ScriptedRandom(10, 100),
            Map.of(DamageType.FIRE, 50),
            Map.of(DamageType.COLD, 50));

        GameActionResult result =
            registry.processPlayerSingleTargetAbility(caster, FIREBALL, "goblin", ROOM_A);

        assertEquals(95, onlyMob(registry).currentHp(),
            "An untyped spell deals its flat damage regardless of a mob's elemental entries");
        assertTrue(containsText(result, "for 5 damage"), "Untyped damage is neither reduced nor amplified");
        assertFalse(containsText(result, "sizzle weakly"),
            "An untyped hit carries no elemental qualifier");
    }

    private MobRegistry buildRegistry(Player caster, int mobHp, RoomId mobRoom, List<String> tags) {
        return buildRegistry(caster, mobHp, mobRoom, tags, MobRegistryTestSupport.random());
    }

    private MobRegistry buildRegistry(
        Player caster, int mobHp, RoomId mobRoom, List<String> tags, CombatRandom random) {
        MobTemplate template = new MobTemplate(
            MobId.of("mob.goblin"),
            "Goblin",
            mobHp,
            UNARMED,
            null,
            false,
            List.of(),
            mobRoom,
            1,
            10,
            5,
            null,
            tags,
            false
        );
        MobTemplateRepository templateRepo = new StubMobTemplateRepository(List.of(template));
        AttackRepository attackRepo = new StubAttackRepository(Map.of(UNARMED, UNARMED_MELEE));
        ItemRepository itemRepo = new StubItemRepository();

        RoomService roomService = new RoomService(new StubRoomRepository(), ROOM_A);
        roomService.ensurePlayerLocation(caster.getUsername());

        StubPlayerRepository playerRepo = new StubPlayerRepository(caster);
        PlayerEventBus bus = new PlayerEventBus();

        MobRegistry registry = new MobRegistry(
            templateRepo, itemRepo, attackRepo, roomService, playerRepo,
            MobRegistryTestSupport.persistenceQueueFor(playerRepo), bus, random);
        registry.init();
        return registry;
    }

    private static boolean containsText(GameActionResult result, String fragment) {
        return result.messages().stream().anyMatch(m -> m.text().contains(fragment));
    }

    private static MobInstance onlyMob(MobRegistry registry) {
        return registry.getMobsInRoom(ROOM_A).get(0);
    }

    @Test
    void harmfulSpell_hitsMob_dealsDamageAndDeductsMana() {
        Player caster = player("merlin");
        MobRegistry registry = buildRegistry(caster, 100, ROOM_A, List.of());

        GameActionResult result =
            registry.processPlayerSingleTargetAbility(caster, FIREBALL, "goblin", ROOM_A);

        assertTrue(containsText(result, "Your fireball strikes the Goblin for 5 damage"),
            "A landing single-target spell should strike the mob for its effect damage");
        assertEquals(95, onlyMob(registry).currentHp(), "The mob should take the spell's 5 damage");
        assertNotNull(result.updatedSource(), "A resolved cast returns the mana-deducted caster");
        assertEquals(16, result.updatedSource().getVitals().getMana(),
            "Mana cost of 4 should be deducted from the starting 20");
        assertTrue(registry.isInCombat(caster.getUsername()),
            "A struck, surviving mob should engage the caster");
    }

    @Test
    void harmfulSpell_canMissMob_dealingNoDamageButEngaging() {
        Player caster = player("merlin");
        // Hit roll 80 exceeds the base hit chance (75), so the spell misses outright.
        MobRegistry registry = buildRegistry(caster, 100, ROOM_A, List.of(), new ScriptedRandom(80));

        GameActionResult result =
            registry.processPlayerSingleTargetAbility(caster, FIREBALL, "goblin", ROOM_A);

        assertTrue(containsText(result, "Your fireball fails to connect with the Goblin"),
            "A missed single-target spell should show the miss flavour line");
        assertEquals(100, onlyMob(registry).currentHp(), "A missed mob takes no damage");
        assertNotNull(result.updatedSource(), "Even a miss spends the ability's cost");
        assertEquals(16, result.updatedSource().getVitals().getMana(),
            "A resolved (but missed) cast still deducts mana so a cooldown can start");
        assertTrue(registry.isInCombat(caster.getUsername()),
            "A missed but surviving mob still engages the caster");
    }

    @Test
    void harmfulSpell_canCritMob_forBonusDamage() {
        Player caster = player("merlin");
        // Hit roll 10 (<= 75 lands) then crit roll 1 (<= 5 crits): 5 damage becomes 5 * multiplier.
        MobRegistry registry = buildRegistry(caster, 100, ROOM_A, List.of(), new ScriptedRandom(10, 1));

        GameActionResult result =
            registry.processPlayerSingleTargetAbility(caster, FIREBALL, "goblin", ROOM_A);

        assertTrue(containsText(result, "A critical hit! Your fireball strikes the Goblin"),
            "A crit should read distinctly from a normal strike");
        int expectedDamage = 5 * CombatSettings.critMultiplier();
        assertEquals(100 - expectedDamage, onlyMob(registry).currentHp(),
            "A crit should deal bonus damage equal to the crit multiplier");
    }

    @Test
    void harmfulSpell_killingBlow_awardsKillRewards() {
        Player caster = player("merlin");
        MobRegistry registry = buildRegistry(caster, 3, ROOM_A, List.of()); // 3 HP < 5 damage → dies

        GameActionResult result =
            registry.processPlayerSingleTargetAbility(caster, FIREBALL, "goblin", ROOM_A);

        assertTrue(containsText(result, "You slay the Goblin"),
            "A mob dropped to zero HP by the spell should be slain");
        assertTrue(containsText(result, "experience points"),
            "A killing blow must award experience");
        assertNotNull(result.updatedSource(), "The caster carries the mana deduction and rewards");
        assertTrue(result.updatedSource().getTotalKills() >= 1,
            "The slain mob should count toward the kill total");
        assertFalse(registry.isInCombat(caster.getUsername()),
            "The only target slain — the caster is left in no combat");
    }

    @Test
    void harmfulSpell_insufficientResources_returnsErrorAndDealsNoDamage() {
        Player caster = player("merlin")
            .withVitals(player("merlin").getVitals().consumeMana(18)); // leaves 2 mana, cost is 4
        MobRegistry registry = buildRegistry(caster, 100, ROOM_A, List.of());

        GameActionResult result =
            registry.processPlayerSingleTargetAbility(caster, FIREBALL, "goblin", ROOM_A);

        assertTrue(containsText(result, "lack the resources"),
            "A caster who cannot pay the cost is rejected");
        assertNull(result.updatedSource(), "A rejected cast must not deduct resources or start a cooldown");
        assertEquals(100, onlyMob(registry).currentHp(), "No damage on a refused cast");
    }

    @Test
    void harmfulSpell_noMatchingMob_returnsError() {
        Player caster = player("merlin");
        MobRegistry registry = buildRegistry(caster, 100, ROOM_A, List.of());

        GameActionResult result =
            registry.processPlayerSingleTargetAbility(caster, FIREBALL, "dragon", ROOM_A);

        assertTrue(containsText(result, "No such target here"),
            "Naming an absent mob should fail with no state change");
        assertNull(result.updatedSource());
    }

    @Test
    void harmfulUndead_onUndeadMob_appliesHolyDamage() {
        Player caster = player("priest");
        MobRegistry registry = buildRegistry(caster, 100, ROOM_A, List.of("undead"));

        GameActionResult result =
            registry.processPlayerSingleTargetAbility(caster, SEARING_LIGHT, "goblin", ROOM_A);

        assertTrue(containsText(result, "Your searing-light strikes the Goblin"),
            "Holy damage should apply to an undead-tagged mob");
        assertEquals(95, onlyMob(registry).currentHp(), "The undead mob should take 5 holy damage");
    }

    @Test
    void harmfulUndead_onNonUndeadMob_hasNoEffect() {
        Player caster = player("priest");
        MobRegistry registry = buildRegistry(caster, 100, ROOM_A, List.of()); // no undead tag

        GameActionResult result =
            registry.processPlayerSingleTargetAbility(caster, SEARING_LIGHT, "goblin", ROOM_A);

        assertTrue(containsText(result, "Your holy power has no effect on that creature"),
            "Holy damage should be refused against a non-undead mob");
        assertNull(result.updatedSource(), "A no-effect cast must not deduct mana");
        assertEquals(100, onlyMob(registry).currentHp(), "A non-undead mob takes no holy damage");
    }

    @Test
    void harmfulOpener_fromStealth_addsBonusDamageAndBreaksStealth() {
        Player caster = player("shadow").withStealth(true);
        MobRegistry registry = buildRegistry(caster, 100, ROOM_A, List.of());

        GameActionResult result =
            registry.processPlayerSingleTargetAbility(caster, BACKSTAB, "goblin", ROOM_A);

        assertTrue(containsText(result, "deadly precision (+10 damage)"),
            "A backstab opened from stealth should add its flat bonus damage");
        // 5 ability damage + 10 stealth bonus = 15.
        assertEquals(85, onlyMob(registry).currentHp(), "The mob should take ability + stealth-bonus damage");
        assertTrue(containsText(result, "You emerge from the shadows"),
            "The strike should break the caster's stealth");
        assertNotNull(result.updatedSource());
        assertFalse(result.updatedSource().isStealthActive(),
            "Stealth should be cleared on the returned caster");
    }

    @Test
    void harmfulOpener_whileAlreadyInCombat_isRefused() {
        Player caster = player("shadow");
        MobRegistry registry = buildRegistry(caster, 100, ROOM_A, List.of());
        // Engage the caster in combat first so the opener gate must reject the backstab.
        registry.processPlayerAttack(caster, "goblin", ROOM_A);
        assertTrue(registry.isInCombat(caster.getUsername()), "Pre-condition: caster is in combat");

        GameActionResult result =
            registry.processPlayerSingleTargetAbility(caster, BACKSTAB, "goblin", ROOM_A);

        assertTrue(containsText(result, "You can only backstab as an opener"),
            "A HARMFUL_OPENER may not be used once the caster is already fighting");
        assertNull(result.updatedSource(), "A refused opener must not deduct resources");
    }

    @Test
    void hasAttackableMob_matchesLiveMobButNotAbsentName() {
        Player caster = player("merlin");
        MobRegistry registry = buildRegistry(caster, 100, ROOM_A, List.of());

        assertTrue(registry.hasAttackableMob(ROOM_A, "goblin"),
            "A live mob in the room should be reported as an attackable target");
        assertFalse(registry.hasAttackableMob(ROOM_A, "dragon"),
            "An absent name should not match any mob");
        assertFalse(registry.hasAttackableMob(ROOM_A, "  "),
            "A blank name should never match");
    }

    // ── stubs ─────────────────────────────────────────────────────────

    /**
     * A {@link CombatRandom} returning a fixed sequence of rolls (each clamped into the requested
     * range) so hit/crit outcomes are fully deterministic. {@link #nextDouble()} returns {@code 1.0}
     * so loot/wander probability gates never consume a scripted roll.
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
