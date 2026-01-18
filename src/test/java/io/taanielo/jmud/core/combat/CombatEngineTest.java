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
import io.taanielo.jmud.core.combat.repository.AttackRepositoryException;
import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectInstance;
import io.taanielo.jmud.core.effects.EffectModifier;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectStacking;
import io.taanielo.jmud.core.effects.ModifierOperation;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

class CombatEngineTest {

    @Test
    void resolvesHitAndDamage() throws Exception {
        AttackId attackId = AttackId.of("attack.test");
        AttackDefinition attack = new AttackDefinition(attackId, "test", 2, 4, 0, 0, 0);
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
        AttackDefinition attack = new AttackDefinition(attackId, "crit", 4, 4, 0, 0, 0);
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
        AttackDefinition attack = new AttackDefinition(attackId, "miss", 2, 2, 0, 0, 0);
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
        AttackDefinition attack = new AttackDefinition(attackId, "rage", 1, 1, 0, 0, 0);
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

    private Player player(String username, List<EffectInstance> effects) {
        PlayerVitals vitals = PlayerVitals.defaults();
        return new Player(
            User.of(Username.of(username), Password.of("pw")),
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
        public Optional<AttackDefinition> findById(AttackId id) throws AttackRepositoryException {
            return Optional.ofNullable(attacks.get(id));
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
