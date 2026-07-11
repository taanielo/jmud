package io.taanielo.jmud.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectInstance;
import io.taanielo.jmud.core.effects.EffectModifier;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectStacking;
import io.taanielo.jmud.core.effects.ModifierOperation;
import io.taanielo.jmud.core.messaging.MessageChannel;
import io.taanielo.jmud.core.messaging.MessagePhase;
import io.taanielo.jmud.core.messaging.MessageSpec;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerEquipment;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.ItemRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

class CombatEngineTest {

    @Test
    void resolvesHitAndDamage() throws Exception {
        AttackId attackId = AttackId.of("attack.test");
        AttackDefinition attack = new AttackDefinition(attackId, "test", 2, 4, 0, 0, 0, List.of());
        CombatEngine engine = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            new FixedCombatRandom(10, 3, 100)
        );
        Player attacker = player("attacker", List.of());
        Player target = player("target", List.of());

        CombatResult result = engine.resolve(attacker, target, attackId);

        assertTrue(result.hit());
        assertFalse(result.crit());
        assertEquals(3, result.damage());
        assertEquals(17, result.target().getVitals().hp());
    }

    @Test
    void resolvesCriticalHit() throws Exception {
        AttackId attackId = AttackId.of("attack.crit");
        AttackDefinition attack = new AttackDefinition(attackId, "crit", 4, 4, 0, 0, 0, List.of());
        CombatEngine engine = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            new FixedCombatRandom(1, 4, 1)
        );
        Player attacker = player("attacker", List.of());
        Player target = player("target", List.of());

        CombatResult result = engine.resolve(attacker, target, attackId);

        assertTrue(result.hit());
        assertTrue(result.crit());
        assertEquals(8, result.damage());
        assertEquals(12, result.target().getVitals().hp());
    }

    @Test
    void resolvesMiss() throws Exception {
        AttackId attackId = AttackId.of("attack.miss");
        AttackDefinition attack = new AttackDefinition(attackId, "miss", 2, 2, 0, 0, 0, List.of());
        CombatEngine engine = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            new FixedCombatRandom(100)
        );
        Player attacker = player("attacker", List.of());
        Player target = player("target", List.of());

        CombatResult result = engine.resolve(attacker, target, attackId);

        assertFalse(result.hit());
        assertEquals(0, result.damage());
        assertEquals(20, result.target().getVitals().hp());
    }

    @Test
    void appliesDamageModifiersFromEffects() throws Exception {
        AttackId attackId = AttackId.of("attack.rage");
        AttackDefinition attack = new AttackDefinition(attackId, "rage", 1, 1, 0, 0, 0, List.of());
        EffectId rageId = EffectId.of("rage");
        EffectDefinition rage = new EffectDefinition(
            rageId,
            "Rage",
            10,
            1,
            EffectStacking.REFRESH,
            List.of(
                new EffectModifier("damage", ModifierOperation.ADD, 2),
                new EffectModifier("damage", ModifierOperation.MULTIPLY, 2)
            ),
            null
        );
        CombatEngine engine = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of(rageId, rage))),
            new FixedCombatRandom(1, 1, 100)
        );
        Player attacker = player("attacker", List.of(new EffectInstance(rageId, 10, 1)));
        Player target = player("target", List.of());

        CombatResult result = engine.resolve(attacker, target, attackId);

        assertEquals(6, result.damage());
        assertEquals(14, result.target().getVitals().hp());
    }

    @Test
    void appliesOnHitEffectWhenChanceRollSucceeds() throws Exception {
        AttackId attackId = AttackId.of("attack.poisonous-bite");
        EffectId poisonId = EffectId.of("poison");
        EffectDefinition poison = new EffectDefinition(
            poisonId,
            "Poison",
            10,
            1,
            EffectStacking.REFRESH,
            List.of(),
            List.of(new MessageSpec(MessagePhase.APPLY, MessageChannel.SELF, "You are poisoned!"))
        );
        AttackDefinition attack = new AttackDefinition(
            attackId, "poisonous bite", 2, 2, 0, 0, 0, List.of(), WeaponType.PIERCING,
            new AttackEffectApplication(poisonId, 50)
        );
        EffectEngine effectEngine = new EffectEngine(new StubEffectRepository(Map.of(poisonId, poison)));
        CombatEngine engine = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            new FixedCombatRandom(10, 2, 100, 25),
            effectEngine
        );
        Player attacker = player("attacker", List.of());
        Player target = player("target", List.of());

        CombatResult result = engine.resolve(attacker, target, attackId);

        assertTrue(result.hit());
        assertEquals(1, result.target().effects().size());
        assertEquals(poisonId, result.target().effects().getFirst().id());
        assertEquals(List.of("You are poisoned!"), result.effectTargetMessages());
    }

    @Test
    void doesNotApplyOnHitEffectWhenChanceRollFails() throws Exception {
        AttackId attackId = AttackId.of("attack.poisonous-bite-fail");
        EffectId poisonId = EffectId.of("poison");
        EffectDefinition poison = new EffectDefinition(
            poisonId, "Poison", 10, 1, EffectStacking.REFRESH, List.of(), null
        );
        AttackDefinition attack = new AttackDefinition(
            attackId, "poisonous bite", 2, 2, 0, 0, 0, List.of(), WeaponType.PIERCING,
            new AttackEffectApplication(poisonId, 50)
        );
        EffectEngine effectEngine = new EffectEngine(new StubEffectRepository(Map.of(poisonId, poison)));
        CombatEngine engine = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            new FixedCombatRandom(10, 2, 100, 75),
            effectEngine
        );
        Player attacker = player("attacker", List.of());
        Player target = player("target", List.of());

        CombatResult result = engine.resolve(attacker, target, attackId);

        assertTrue(result.hit());
        assertTrue(result.target().effects().isEmpty());
        assertTrue(result.effectTargetMessages().isEmpty());
    }

    @Test
    void doesNotApplyOnHitEffectWhenAttackMisses() throws Exception {
        AttackId attackId = AttackId.of("attack.poisonous-bite-miss");
        EffectId poisonId = EffectId.of("poison");
        EffectDefinition poison = new EffectDefinition(
            poisonId, "Poison", 10, 1, EffectStacking.REFRESH, List.of(), null
        );
        AttackDefinition attack = new AttackDefinition(
            attackId, "poisonous bite", 2, 2, 0, 0, 0, List.of(), WeaponType.PIERCING,
            new AttackEffectApplication(poisonId, 100)
        );
        EffectEngine effectEngine = new EffectEngine(new StubEffectRepository(Map.of(poisonId, poison)));
        CombatEngine engine = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            new FixedCombatRandom(100),
            effectEngine
        );
        Player attacker = player("attacker", List.of());
        Player target = player("target", List.of());

        CombatResult result = engine.resolve(attacker, target, attackId);

        assertFalse(result.hit());
        assertTrue(result.target().effects().isEmpty());
        assertTrue(result.effectTargetMessages().isEmpty());
    }

    @Test
    void raceArmorBonusReducesHitChance() throws Exception {
        // Base hit chance is 75. With armor bonus 3, effective hit chance becomes 72.
        // Rolling 73 would normally hit (73 <= 75) but misses with armor bonus (73 > 72).
        AttackId attackId = AttackId.of("attack.armor");
        AttackDefinition attack = new AttackDefinition(attackId, "armor", 2, 2, 0, 0, 0, List.of());
        CombatEngine engine = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            new StubArmorBonusResolver(3),
            new FixedCombatRandom(73)
        );
        Player attacker = player("attacker", List.of());
        Player target = player("target", List.of());

        CombatResult result = engine.resolve(attacker, target, attackId);

        assertFalse(result.hit());
        assertEquals(0, result.damage());
    }

    @Test
    void classArmorBonusReducesHitChance() throws Exception {
        // Base hit chance is 75. With class armor bonus 3, effective hit chance becomes 72.
        // Rolling 73 would normally hit (73 <= 75) but misses with the class bonus (73 > 72).
        AttackId attackId = AttackId.of("attack.class-armor");
        AttackDefinition attack = new AttackDefinition(attackId, "class-armor", 2, 2, 0, 0, 0, List.of());
        FixedCombatRandom random = new FixedCombatRandom(73);
        CombatEngine engine = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            RaceArmorBonusResolver.noOp(),
            RaceAttackBonusResolver.noOp(),
            new StubClassArmorBonusResolver(3),
            EquipmentArmorResolver.noOp(),
            ShieldBlockResolver.noOp(),
            (tick, actorId) -> random,
            () -> 0L,
            null
        );
        Player attacker = player("attacker", List.of());
        Player target = player("target", List.of());

        CombatResult result = engine.resolve(attacker, target, attackId);

        assertFalse(result.hit());
        assertEquals(0, result.damage());
    }

    @Test
    void raceAttackBonusIncreasesHitChance() throws Exception {
        // Base hit chance is 75. With attack bonus +2, effective hit chance becomes 77.
        // Rolling 76 would normally miss (76 > 75) but hits with the attack bonus (76 <= 77).
        AttackId attackId = AttackId.of("attack.strong");
        AttackDefinition attack = new AttackDefinition(attackId, "strong", 2, 2, 0, 0, 0, List.of());
        FixedCombatRandom random = new FixedCombatRandom(76, 2, 100);
        CombatEngine engine = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            RaceArmorBonusResolver.noOp(),
            new StubAttackBonusResolver(2),
            EquipmentArmorResolver.noOp(),
            (tick, actorId) -> random,
            () -> 0L,
            null
        );
        Player attacker = player("attacker", List.of());
        Player target = player("target", List.of());

        CombatResult result = engine.resolve(attacker, target, attackId);

        assertTrue(result.hit());
        assertEquals(2, result.damage());
    }

    @Test
    void raceArmorBonusZeroLeavesHitChanceUnchanged() throws Exception {
        // Base hit chance is 75. With armor bonus 0, rolling 75 still hits.
        AttackId attackId = AttackId.of("attack.no-armor");
        AttackDefinition attack = new AttackDefinition(attackId, "no-armor", 2, 2, 0, 0, 0, List.of());
        CombatEngine engine = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            new StubArmorBonusResolver(0),
            new FixedCombatRandom(75, 2, 100)
        );
        Player attacker = player("attacker", List.of());
        Player target = player("target", List.of());

        CombatResult result = engine.resolve(attacker, target, attackId);

        assertTrue(result.hit());
    }

    @Test
    void environmentHitModifierReducesHitChance() throws Exception {
        // Base hit chance 75; a -5 environment (weather) modifier lowers it to 70. Rolling 73
        // would hit at 75 but misses at 70.
        AttackId attackId = AttackId.of("attack.weather");
        AttackDefinition attack = new AttackDefinition(attackId, "weather", 2, 2, 0, 0, 0, List.of());
        CombatEngine engine = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            new FixedCombatRandom(73)
        );

        CombatResult result = engine.resolve(
            new CombatAction(player("attacker", List.of()), player("target", List.of()), attackId, -5, 0));

        assertFalse(result.hit());
        assertEquals(0, result.damage());
    }

    @Test
    void rangedEnvironmentModifierAppliesOnlyToRangedAttacks() throws Exception {
        // Base hit chance 75. A ranged environment modifier of -10 applies only to ranged attacks
        // (lowering them to 65); a melee attack keeps 75. Rolling 70 therefore hits melee, misses ranged.
        AttackId meleeId = AttackId.of("attack.melee-weather");
        AttackDefinition melee = new AttackDefinition(meleeId, "melee", 2, 2, 0, 0, 0, List.of());
        AttackId rangedId = AttackId.of("attack.ranged-weather");
        AttackDefinition ranged = new AttackDefinition(
            rangedId, "ranged", 2, 2, 0, 0, 0, List.of(), WeaponType.PIERCING, null, RangeType.RANGED);
        Map<AttackId, AttackDefinition> attacks = Map.of(meleeId, melee, rangedId, ranged);

        CombatEngine meleeEngine = new CombatEngine(
            new StubAttackRepository(attacks),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            new FixedCombatRandom(70, 2, 100)
        );
        CombatResult meleeResult = meleeEngine.resolve(
            new CombatAction(player("attacker", List.of()), player("target", List.of()), meleeId, 0, -10));
        assertTrue(meleeResult.hit(), "ranged modifier must not apply to a melee attack");

        CombatEngine rangedEngine = new CombatEngine(
            new StubAttackRepository(attacks),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            new FixedCombatRandom(70)
        );
        CombatResult rangedResult = rangedEngine.resolve(
            new CombatAction(player("attacker", List.of()), player("target", List.of()), rangedId, 0, -10));
        assertFalse(rangedResult.hit(), "ranged modifier must lower a ranged attack's hit chance");
    }

    // ── Seeded / deterministic tests ──────────────────────────────────────

    @Test
    void seededEngineProducesIdenticalResultsForSameInputs() throws Exception {
        AttackId attackId = AttackId.of("attack.seed-test");
        AttackDefinition attack = new AttackDefinition(attackId, "seed-test", 2, 6, 0, 0, 0, List.of());
        long worldSeed = 0xABCDEF01L;
        long tick = 42L;
        SeededCombatRandomProvider provider = new SeededCombatRandomProvider(worldSeed);

        CombatEngine engine1 = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            RaceArmorBonusResolver.noOp(),
            EquipmentArmorResolver.noOp(),
            provider,
            () -> tick
        );
        CombatEngine engine2 = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            RaceArmorBonusResolver.noOp(),
            EquipmentArmorResolver.noOp(),
            provider,
            () -> tick
        );

        Player attacker = player("attacker", List.of());
        Player target = player("target", List.of());

        CombatResult r1 = engine1.resolve(attacker, target, attackId);
        CombatResult r2 = engine2.resolve(attacker, target, attackId);

        assertEquals(r1.hit(), r2.hit(), "Same seed should produce same hit result");
        assertEquals(r1.crit(), r2.crit(), "Same seed should produce same crit result");
        assertEquals(r1.damage(), r2.damage(), "Same seed should produce same damage");
        assertEquals(r1.rngSeed(), r2.rngSeed(), "RNG seed should be recorded in result");
        assertTrue(r1.rngSeed() != 0L, "Seeded engine should record a non-zero seed");
    }

    @Test
    void seededEngineRecordsNonZeroSeedInResult() throws Exception {
        AttackId attackId = AttackId.of("attack.record-seed");
        AttackDefinition attack = new AttackDefinition(attackId, "record-seed", 1, 4, 0, 0, 0, List.of());
        SeededCombatRandomProvider provider = new SeededCombatRandomProvider(999L);

        CombatEngine engine = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            RaceArmorBonusResolver.noOp(),
            EquipmentArmorResolver.noOp(),
            provider,
            () -> 1L
        );

        CombatResult result = engine.resolve(player("attacker", List.of()), player("target", List.of()), attackId);

        assertTrue(result.rngSeed() != 0L,
            "Seeded engine should record the per-encounter seed in the result");
    }

    @Test
    void differentActorsProduceDifferentSeeds() throws Exception {
        AttackId attackId = AttackId.of("attack.actors");
        AttackDefinition attack = new AttackDefinition(attackId, "actors", 2, 6, 0, 0, 0, List.of());
        SeededCombatRandomProvider provider = new SeededCombatRandomProvider(12345L);
        long tick = 10L;

        CombatEngine engine = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            RaceArmorBonusResolver.noOp(),
            EquipmentArmorResolver.noOp(),
            provider,
            () -> tick
        );

        Player target = player("target", List.of());
        CombatResult r1 = engine.resolve(player("alice", List.of()), target, attackId);
        // Engine re-queries tick on each resolve; create separate engine to reset RNG state
        CombatEngine engine2 = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            RaceArmorBonusResolver.noOp(),
            EquipmentArmorResolver.noOp(),
            provider,
            () -> tick
        );
        CombatResult r2 = engine2.resolve(player("bob", List.of()), target, attackId);

        assertTrue(r1.rngSeed() != r2.rngSeed(),
            "Different actors at the same tick should produce different seeds");
    }

    @Test
    void legacyCombatRandomConstructorStillWorks() throws Exception {
        AttackId attackId = AttackId.of("attack.legacy");
        AttackDefinition attack = new AttackDefinition(attackId, "legacy", 2, 4, 0, 0, 0, List.of());
        CombatEngine engine = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            new FixedCombatRandom(10, 3, 100)
        );

        CombatResult result = engine.resolve(player("attacker", List.of()), player("target", List.of()), attackId);

        assertTrue(result.hit(), "Legacy constructor should still produce correct combat results");
        assertEquals(3, result.damage(), "Legacy constructor should produce correct damage");
        assertEquals(0L, result.rngSeed(), "Legacy fixed-random constructor records seed 0");
    }

    // ── Shield block tests ────────────────────────────────────────────────

    @Test
    void shieldBlockReducesDamageAndIsDistinctFromHitAndCrit() throws Exception {
        // hit roll 10 (hit), damage roll 4, block roll 1 (<= 100 block chance). No crit roll: a
        // block precludes a crit. 50% reduction on 4 damage => 2.
        AttackId attackId = AttackId.of("attack.block");
        AttackDefinition attack = new AttackDefinition(attackId, "block", 4, 4, 0, 0, 0, List.of());
        ItemId shieldId = ItemId.of("test-shield");
        CombatEngine engine = shieldEngine(attack, shieldItem(shieldId, 100, 50), shieldId,
            new FixedCombatRandom(10, 4, 1));
        Player attacker = player("attacker", List.of());
        Player target = playerWithOffhand("target", shieldId);

        CombatResult result = engine.resolve(attacker, target, attackId);

        assertTrue(result.hit(), "a blocked attack is still a hit");
        assertTrue(result.blocked(), "the shield should block");
        assertFalse(result.crit(), "a block precludes a crit");
        assertEquals(2, result.damage(), "block reduces damage by 50%, not to zero");
        assertEquals(18, result.target().getVitals().hp());
    }

    @Test
    void failedShieldRollLeavesAttackUnblocked() throws Exception {
        // hit roll 10, damage roll 4, block roll 50 (> 25 block chance => no block), crit roll 100.
        AttackId attackId = AttackId.of("attack.no-block");
        AttackDefinition attack = new AttackDefinition(attackId, "no-block", 4, 4, 0, 0, 0, List.of());
        ItemId shieldId = ItemId.of("test-shield");
        CombatEngine engine = shieldEngine(attack, shieldItem(shieldId, 25, 50), shieldId,
            new FixedCombatRandom(10, 4, 50, 100));
        Player attacker = player("attacker", List.of());
        Player target = playerWithOffhand("target", shieldId);

        CombatResult result = engine.resolve(attacker, target, attackId);

        assertTrue(result.hit());
        assertFalse(result.blocked());
        assertEquals(4, result.damage(), "an unblocked hit deals full damage");
    }

    @Test
    void noBlockRollWhenOffhandIsEmpty() throws Exception {
        // Only three rolls are supplied (hit, damage, crit). If a block roll were consumed the
        // FixedCombatRandom would run out, so this asserts no block roll happens without a shield.
        AttackId attackId = AttackId.of("attack.empty-offhand");
        AttackDefinition attack = new AttackDefinition(attackId, "empty-offhand", 4, 4, 0, 0, 0, List.of());
        CombatEngine engine = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            new FixedCombatRandom(10, 4, 100)
        );
        Player attacker = player("attacker", List.of());
        Player target = player("target", List.of());

        CombatResult result = engine.resolve(attacker, target, attackId);

        assertTrue(result.hit());
        assertFalse(result.blocked());
        assertEquals(4, result.damage());
    }

    @Test
    void noBlockRollWhenOffhandItemHasNoBlockStat() throws Exception {
        // A charm in the off-hand slot with no block_chance behaves exactly as an empty slot: only
        // hit, damage and crit rolls are consumed.
        AttackId attackId = AttackId.of("attack.charm-offhand");
        AttackDefinition attack = new AttackDefinition(attackId, "charm-offhand", 4, 4, 0, 0, 0, List.of());
        ItemId charmId = ItemId.of("test-charm");
        Item charm = Item.builder(charmId, "Test Charm", "A trinket.", new ItemAttributes(Map.of("strength", 1)))
            .equipSlot(EquipmentSlot.OFFHAND).weight(1).value(0).build();
        CombatEngine engine = shieldEngine(attack, charm, charmId, new FixedCombatRandom(10, 4, 100));
        Player attacker = player("attacker", List.of());
        Player target = playerWithOffhand("target", charmId);

        CombatResult result = engine.resolve(attacker, target, attackId);

        assertTrue(result.hit());
        assertFalse(result.blocked());
        assertEquals(4, result.damage());
    }

    @Test
    void blockStacksWithHitChanceModifiers() throws Exception {
        // Class armour bonus 3 lowers hit chance 75 -> 72; roll 70 still hits, then the shield blocks.
        AttackId attackId = AttackId.of("attack.armor-block");
        AttackDefinition attack = new AttackDefinition(attackId, "armor-block", 4, 4, 0, 0, 0, List.of());
        ItemId shieldId = ItemId.of("test-shield");
        FixedCombatRandom random = new FixedCombatRandom(70, 4, 1);
        ShieldBlockResolver shieldResolver = shieldResolver(shieldId, shieldItem(shieldId, 100, 50));
        CombatEngine engine = new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            RaceArmorBonusResolver.noOp(),
            RaceAttackBonusResolver.noOp(),
            new StubClassArmorBonusResolver(3),
            EquipmentArmorResolver.noOp(),
            shieldResolver,
            (tick, actorId) -> random,
            () -> 0L,
            null
        );
        Player attacker = player("attacker", List.of());
        Player target = playerWithOffhand("target", shieldId);

        CombatResult result = engine.resolve(attacker, target, attackId);

        assertTrue(result.hit());
        assertTrue(result.blocked());
        assertEquals(2, result.damage());
    }

    @Test
    void blockProducesDistinctDefaultMessages() throws Exception {
        AttackId attackId = AttackId.of("attack.block-msg");
        AttackDefinition attack = new AttackDefinition(attackId, "block-msg", 4, 4, 0, 0, 0, List.of());
        ItemId shieldId = ItemId.of("test-shield");
        CombatEngine engine = shieldEngine(attack, shieldItem(shieldId, 100, 50), shieldId,
            new FixedCombatRandom(10, 4, 1));

        CombatResult result = engine.resolve(
            player("attacker", List.of()), playerWithOffhand("target", shieldId), attackId);

        assertTrue(result.blocked());
        assertEquals("target blocks your attack.", result.sourceMessage());
        assertEquals("You block attacker's attack with your shield.", result.targetMessage());
        assertEquals("target blocks attacker's attack.", result.roomMessage());
    }

    @Test
    void seededEngineWithShieldIsDeterministic() throws Exception {
        AttackId attackId = AttackId.of("attack.block-seed");
        AttackDefinition attack = new AttackDefinition(attackId, "block-seed", 2, 6, 0, 0, 0, List.of());
        ItemId shieldId = ItemId.of("test-shield");
        SeededCombatRandomProvider provider = new SeededCombatRandomProvider(0x5EEDL);
        ShieldBlockResolver shieldResolver = shieldResolver(shieldId, shieldItem(shieldId, 50, 50));

        CombatResult r1 = seededShieldEngine(attack, attackId, shieldResolver, provider)
            .resolve(player("attacker", List.of()), playerWithOffhand("target", shieldId), attackId);
        CombatResult r2 = seededShieldEngine(attack, attackId, shieldResolver, provider)
            .resolve(player("attacker", List.of()), playerWithOffhand("target", shieldId), attackId);

        assertEquals(r1.hit(), r2.hit());
        assertEquals(r1.blocked(), r2.blocked());
        assertEquals(r1.damage(), r2.damage());
        assertEquals(r1.rngSeed(), r2.rngSeed());
    }

    @Test
    void dualWieldAddsSecondOffhandAttack() throws Exception {
        AttackId mainId = AttackId.of("attack.main");
        AttackId offId = AttackId.of("attack.parry");
        AttackDefinition mainAttack = new AttackDefinition(mainId, "sword", 4, 4, 0, 0, 0, List.of());
        AttackDefinition offAttack = new AttackDefinition(offId, "parry", 2, 2, 0, 0, 0, List.of());
        // Rolls: main hit=10, main dmg=4, main crit=100(no); off hit=10, off dmg=2, off crit=100(no).
        FixedCombatRandom random = new FixedCombatRandom(10, 4, 100, 10, 2, 100);
        CombatEngine engine = dualWieldEngine(Map.of(mainId, mainAttack, offId, offAttack), random);
        Player attacker = dualWielder("attacker", offhandWeapon(ItemId.of("parry"), "Parrying Dagger", offId));
        Player target = player("target", List.of());

        CombatResult result = engine.resolve(attacker, target, mainId);

        assertTrue(result.hit());
        assertEquals(4, result.damage());
        assertNotNull(result.offhand());
        assertTrue(result.offhand().hit());
        // Off-hand deals 50% damage: round(2 * 0.5) = 1.
        assertEquals(1, result.offhand().damage());
        // Both hits land on the same target this round: 20 - 4 - 1 = 15.
        assertEquals(15, result.target().getVitals().hp());
    }

    @Test
    void offhandAttackUsesDistinctWeaponNamedMessages() throws Exception {
        AttackId mainId = AttackId.of("attack.main2");
        AttackId offId = AttackId.of("attack.parry2");
        AttackDefinition mainAttack = new AttackDefinition(mainId, "sword", 4, 4, 0, 0, 0, List.of());
        AttackDefinition offAttack = new AttackDefinition(offId, "parry", 2, 2, 0, 0, 0, List.of());
        FixedCombatRandom random = new FixedCombatRandom(10, 4, 100, 10, 2, 100);
        CombatEngine engine = dualWieldEngine(Map.of(mainId, mainAttack, offId, offAttack), random);
        Player attacker = dualWielder("attacker", offhandWeapon(ItemId.of("parry"), "Parrying Dagger", offId));
        Player target = player("target", List.of());

        CombatResult result = engine.resolve(attacker, target, mainId);

        assertEquals("You hit target for 4.", result.sourceMessage());
        assertEquals("Your off-hand Parrying Dagger hits target for 1.", result.offhand().sourceMessage());
        assertEquals("attacker's off-hand Parrying Dagger hits you for 1.", result.offhand().targetMessage());
        assertEquals("attacker's off-hand Parrying Dagger hits target.", result.offhand().roomMessage());
    }

    @Test
    void offhandAttackCanMissIndependently() throws Exception {
        AttackId mainId = AttackId.of("attack.main3");
        AttackId offId = AttackId.of("attack.parry3");
        AttackDefinition mainAttack = new AttackDefinition(mainId, "sword", 4, 4, 0, 0, 0, List.of());
        AttackDefinition offAttack = new AttackDefinition(offId, "parry", 2, 2, 0, 0, 0, List.of());
        // Main hits (10), off-hand misses (60 > offhand hit chance of 50).
        FixedCombatRandom random = new FixedCombatRandom(10, 4, 100, 60);
        CombatEngine engine = dualWieldEngine(Map.of(mainId, mainAttack, offId, offAttack), random);
        Player attacker = dualWielder("attacker", offhandWeapon(ItemId.of("parry"), "Parrying Dagger", offId));
        Player target = player("target", List.of());

        CombatResult result = engine.resolve(attacker, target, mainId);

        assertTrue(result.hit());
        assertNotNull(result.offhand());
        assertFalse(result.offhand().hit());
        assertEquals(0, result.offhand().damage());
        // Only the main-hand hit lands: 20 - 4 = 16.
        assertEquals(16, result.target().getVitals().hp());
        assertEquals("Your off-hand Parrying Dagger misses target.", result.offhand().sourceMessage());
    }

    @Test
    void withoutOffhandWeaponRoundIsSingleAttack() throws Exception {
        // The dual-wield resolver is enabled, but an empty off-hand slot must leave combat identical
        // to a single-attack round — no extra rolls consumed, no off-hand result.
        AttackId mainId = AttackId.of("attack.solo");
        AttackDefinition mainAttack = new AttackDefinition(mainId, "sword", 4, 4, 0, 0, 0, List.of());
        FixedCombatRandom random = new FixedCombatRandom(10, 4, 100);
        CombatEngine engine = dualWieldEngine(Map.of(mainId, mainAttack), random);
        Player attacker = player("attacker", List.of());
        Player target = player("target", List.of());

        CombatResult result = engine.resolve(attacker, target, mainId);

        assertTrue(result.hit());
        assertEquals(4, result.damage());
        assertNull(result.offhand());
        assertEquals(16, result.target().getVitals().hp());
    }

    private CombatEngine dualWieldEngine(
        Map<AttackId, AttackDefinition> attacks, FixedCombatRandom random) {
        return new CombatEngine(
            new StubAttackRepository(attacks),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            RaceArmorBonusResolver.noOp(),
            RaceAttackBonusResolver.noOp(),
            ClassArmorBonusResolver.noOp(),
            EquipmentArmorResolver.noOp(),
            ShieldBlockResolver.noOp(),
            new OffhandAttackResolver(),
            (tick, actorId) -> random,
            () -> 0L,
            null
        );
    }

    private Item offhandWeapon(ItemId id, String name, AttackId attackRef) {
        return Item.builder(id, name, "A test off-hand weapon.", new ItemAttributes(Map.of()))
            .equipSlot(EquipmentSlot.OFFHAND).weight(1).value(0).attackRef(attackRef).build();
    }

    private Player dualWielder(String username, Item offhand) {
        return player(username, List.of())
            .withInventory(List.of(offhand))
            .withEquipment(PlayerEquipment.empty().equip(EquipmentSlot.OFFHAND, offhand.getId()));
    }

    private CombatEngine seededShieldEngine(
        AttackDefinition attack, AttackId attackId, ShieldBlockResolver shieldResolver,
        SeededCombatRandomProvider provider) {
        return new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            RaceArmorBonusResolver.noOp(),
            RaceAttackBonusResolver.noOp(),
            ClassArmorBonusResolver.noOp(),
            EquipmentArmorResolver.noOp(),
            shieldResolver,
            provider,
            () -> 7L,
            null
        );
    }

    private CombatEngine shieldEngine(
        AttackDefinition attack, Item offhandItem, ItemId offhandId, FixedCombatRandom random) {
        AttackId attackId = attack.id();
        return new CombatEngine(
            new StubAttackRepository(Map.of(attackId, attack)),
            new CombatModifierResolver(new StubEffectRepository(Map.of())),
            RaceArmorBonusResolver.noOp(),
            RaceAttackBonusResolver.noOp(),
            ClassArmorBonusResolver.noOp(),
            EquipmentArmorResolver.noOp(),
            shieldResolver(offhandId, offhandItem),
            (tick, actorId) -> random,
            () -> 0L,
            null
        );
    }

    private ShieldBlockResolver shieldResolver(ItemId offhandId, Item offhandItem) {
        ItemRepository repository = new ItemRepository() {
            @Override
            public void save(Item item) {
            }

            @Override
            public Optional<Item> findById(ItemId id) {
                return id.equals(offhandId) ? Optional.of(offhandItem) : Optional.empty();
            }
        };
        return new ShieldBlockResolver(repository);
    }

    private Item shieldItem(ItemId id, int blockChance, int blockReduction) {
        return Item.builder(id, "Test Shield", "A test shield.",
                new ItemAttributes(Map.of("block_chance", blockChance, "block_reduction", blockReduction)))
            .equipSlot(EquipmentSlot.OFFHAND).weight(1).value(0).build();
    }

    private Player playerWithOffhand(String username, ItemId offhandId) {
        return player(username, List.of())
            .withEquipment(PlayerEquipment.empty().equip(EquipmentSlot.OFFHAND, offhandId));
    }

    private Player player(String username, List<EffectInstance> effects) {
        PlayerVitals vitals = PlayerVitals.defaults();
        return new Player(
            User.of(Username.of(username), Password.hash("pw", 1000)),
            1,
            0,
            vitals,
            new ArrayList<>(effects),
            "prompt",
            false,
            List.of(),
            null,
            null
        );
    }

    private static class FixedCombatRandom implements CombatRandom {
        private final int[] rolls;
        private int index;

        private FixedCombatRandom(int... rolls) {
            this.rolls = rolls;
        }

        @Override
        public int roll(int minInclusive, int maxInclusive) {
            if (index >= rolls.length) {
                throw new IllegalStateException("No more rolls available");
            }
            return rolls[index++];
        }
    }

    private static class StubAttackRepository implements AttackRepository {
        private final Map<AttackId, AttackDefinition> attacks;

        private StubAttackRepository(Map<AttackId, AttackDefinition> attacks) {
            this.attacks = attacks;
        }

        @Override
        public Optional<AttackDefinition> findById(AttackId id) throws RepositoryException {
            return Optional.ofNullable(attacks.get(id));
        }
    }

    private static class StubArmorBonusResolver extends RaceArmorBonusResolver {
        private final int bonus;

        private StubArmorBonusResolver(int bonus) {
            super(emptyRaceRepository());
            this.bonus = bonus;
        }

        @Override
        public int armorBonus(Player player) {
            return bonus;
        }

        private static io.taanielo.jmud.core.character.repository.RaceRepository emptyRaceRepository() {
            return new io.taanielo.jmud.core.character.repository.RaceRepository() {
                @Override
                public Optional<io.taanielo.jmud.core.character.Race> findById(
                    io.taanielo.jmud.core.character.RaceId id) {
                    return Optional.empty();
                }

                @Override
                public java.util.List<io.taanielo.jmud.core.character.Race> findAll() {
                    return List.of();
                }
            };
        }
    }

    private static class StubAttackBonusResolver extends RaceAttackBonusResolver {
        private final int bonus;

        private StubAttackBonusResolver(int bonus) {
            super(emptyRaceRepository());
            this.bonus = bonus;
        }

        @Override
        public int attackBonus(Player attacker) {
            return bonus;
        }

        private static io.taanielo.jmud.core.character.repository.RaceRepository emptyRaceRepository() {
            return new io.taanielo.jmud.core.character.repository.RaceRepository() {
                @Override
                public Optional<io.taanielo.jmud.core.character.Race> findById(
                    io.taanielo.jmud.core.character.RaceId id) {
                    return Optional.empty();
                }

                @Override
                public java.util.List<io.taanielo.jmud.core.character.Race> findAll() {
                    return List.of();
                }
            };
        }
    }

    private static class StubClassArmorBonusResolver extends ClassArmorBonusResolver {
        private final int bonus;

        private StubClassArmorBonusResolver(int bonus) {
            super(emptyClassRepository());
            this.bonus = bonus;
        }

        @Override
        public int armorBonus(Player player) {
            return bonus;
        }

        private static io.taanielo.jmud.core.character.repository.ClassRepository emptyClassRepository() {
            return new io.taanielo.jmud.core.character.repository.ClassRepository() {
                @Override
                public Optional<io.taanielo.jmud.core.character.ClassDefinition> findById(
                    io.taanielo.jmud.core.character.ClassId id) {
                    return Optional.empty();
                }

                @Override
                public java.util.List<io.taanielo.jmud.core.character.ClassDefinition> findAll() {
                    return List.of();
                }
            };
        }
    }

    private static class StubEffectRepository implements EffectRepository {
        private final Map<EffectId, EffectDefinition> definitions;

        private StubEffectRepository(Map<EffectId, EffectDefinition> definitions) {
            this.definitions = definitions;
        }

        @Override
        public Optional<EffectDefinition> findById(EffectId id) {
            return Optional.ofNullable(definitions.get(id));
        }
    }
}
