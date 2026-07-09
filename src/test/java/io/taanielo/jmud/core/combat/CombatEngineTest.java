package io.taanielo.jmud.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import io.taanielo.jmud.core.player.PlayerVitals;
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
