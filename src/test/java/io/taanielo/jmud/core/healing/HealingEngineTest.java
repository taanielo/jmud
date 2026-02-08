package io.taanielo.jmud.core.healing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectInstance;
import io.taanielo.jmud.core.effects.EffectModifier;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectStacking;
import io.taanielo.jmud.core.effects.ModifierOperation;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

class HealingEngineTest {

    @Test
    void healsBaseAmount() throws Exception {
        PlayerVitals vitals = new PlayerVitals(10, 20, 10, 20, 10, 20);
        Player player = new Player(
            User.of(Username.of("sparky"), Password.hash("pw", 1000)),
            1,
            0,
            vitals,
            new ArrayList<>(),
            "prompt",
            false,
            List.of(),
            null,
            null
        );
        HealingEngine engine = new HealingEngine(new StubEffectRepository(Map.of()));

        Player updated = engine.apply(player, 2);

        assertEquals(12, updated.getVitals().hp());
        assertEquals(20, updated.getVitals().maxHp());
        assertEquals(20, updated.getVitals().baseMaxHp());
    }

    @Test
    void stacksHealingModifiers() throws Exception {
        EffectId regenId = EffectId.of("regen");
        EffectDefinition regen = new EffectDefinition(
            regenId,
            "Regen",
            10,
            1,
            EffectStacking.STACK,
            List.of(new EffectModifier(HealingModifierKeys.HEAL_PER_TICK, ModifierOperation.ADD, 2)),
            null
        );
        PlayerVitals vitals = new PlayerVitals(10, 20, 10, 20, 10, 20);
        List<EffectInstance> effects = new ArrayList<>();
        effects.add(new EffectInstance(regenId, 10, 2));
        Player player = new Player(
            User.of(Username.of("sparky"), Password.hash("pw", 1000)),
            1,
            0,
            vitals,
            effects,
            "prompt",
            false,
            List.of(),
            null,
            null
        );
        HealingEngine engine = new HealingEngine(new StubEffectRepository(Map.of(regenId, regen)));

        Player updated = engine.apply(player, 1);

        assertEquals(15, updated.getVitals().hp());
    }

    @Test
    void respectsMaxHpModifiersAndRevertsWhenEffectExpires() throws Exception {
        EffectId fortifyId = EffectId.of("fortify");
        EffectDefinition fortify = new EffectDefinition(
            fortifyId,
            "Fortify",
            10,
            1,
            EffectStacking.REFRESH,
            List.of(new EffectModifier(HealingModifierKeys.MAX_HP, ModifierOperation.ADD, 5)),
            null
        );
        PlayerVitals vitals = new PlayerVitals(20, 20, 10, 20, 10, 20);
        List<EffectInstance> effects = new ArrayList<>();
        effects.add(new EffectInstance(fortifyId, 10, 1));
        Player player = new Player(
            User.of(Username.of("sparky"), Password.hash("pw", 1000)),
            1,
            0,
            vitals,
            effects,
            "prompt",
            false,
            List.of(),
            null,
            null
        );
        HealingEngine engine = new HealingEngine(new StubEffectRepository(Map.of(fortifyId, fortify)));

        Player boosted = engine.apply(player, 1);

        assertEquals(25, boosted.getVitals().maxHp());
        assertEquals(21, boosted.getVitals().hp());
        assertEquals(20, boosted.getVitals().baseMaxHp());

        boosted.effects().clear();
        Player reverted = engine.apply(boosted, 1);

        assertEquals(20, reverted.getVitals().maxHp());
        assertEquals(20, reverted.getVitals().hp());
        assertEquals(20, reverted.getVitals().baseMaxHp());
    }

    @Test
    void keepsEffectsWhenHpIsMax() throws Exception {
        EffectId regenId = EffectId.of("regen");
        EffectDefinition regen = new EffectDefinition(
            regenId,
            "Regen",
            10,
            1,
            EffectStacking.REFRESH,
            List.of(new EffectModifier(HealingModifierKeys.HEAL_PER_TICK, ModifierOperation.ADD, 2)),
            null
        );
        PlayerVitals vitals = new PlayerVitals(20, 20, 10, 20, 10, 20);
        List<EffectInstance> effects = new ArrayList<>();
        effects.add(new EffectInstance(regenId, 10, 1));
        Player player = new Player(
            User.of(Username.of("sparky"), Password.hash("pw", 1000)),
            1,
            0,
            vitals,
            effects,
            "prompt",
            false,
            List.of(),
            null,
            null
        );
        HealingEngine engine = new HealingEngine(new StubEffectRepository(Map.of(regenId, regen)));

        Player updated = engine.apply(player, 2);

        assertSame(player.effects(), updated.effects());
        assertEquals(1, updated.effects().size());
    }

    @Test
    void appliesDamagePerTickModifiers() throws Exception {
        EffectId poisonId = EffectId.of("poison");
        EffectDefinition poison = new EffectDefinition(
            poisonId,
            "Poison",
            10,
            1,
            EffectStacking.REFRESH,
            List.of(new EffectModifier(HealingModifierKeys.DAMAGE_PER_TICK, ModifierOperation.ADD, 5)),
            null
        );
        PlayerVitals vitals = new PlayerVitals(12, 20, 10, 20, 10, 20);
        List<EffectInstance> effects = new ArrayList<>();
        effects.add(new EffectInstance(poisonId, 10, 1));
        Player player = new Player(
            User.of(Username.of("sparky"), Password.hash("pw", 1000)),
            1,
            0,
            vitals,
            effects,
            "prompt",
            false,
            List.of(),
            null,
            null
        );
        HealingEngine engine = new HealingEngine(new StubEffectRepository(Map.of(poisonId, poison)));

        Player updated = engine.apply(player, 0);

        assertEquals(7, updated.getVitals().hp());
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
