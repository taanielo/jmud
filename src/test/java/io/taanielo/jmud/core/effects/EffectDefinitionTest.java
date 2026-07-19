package io.taanielo.jmud.core.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class EffectDefinitionTest {

    private static EffectDefinition withModifiers(List<EffectModifier> modifiers) {
        return new EffectDefinition(
            EffectId.of("test"),
            "Test",
            10,
            1,
            EffectStacking.REFRESH,
            modifiers,
            List.of()
        );
    }

    @Test
    void beneficialWhenModifiersRaiseGoodStats() {
        EffectDefinition bless = withModifiers(List.of(
            new EffectModifier("defense", ModifierOperation.ADD, 2),
            new EffectModifier("hit_chance", ModifierOperation.ADD, 1)
        ));

        assertFalse(bless.isHarmful());
    }

    @Test
    void harmfulWhenModifiersLowerGoodStats() {
        EffectDefinition dazed = withModifiers(List.of(
            new EffectModifier("attack", ModifierOperation.ADD, -3),
            new EffectModifier("hit_chance", ModifierOperation.ADD, -3)
        ));

        assertTrue(dazed.isHarmful());
    }

    @Test
    void harmfulWhenPositiveDamagePerTick() {
        EffectDefinition poison = withModifiers(List.of(
            new EffectModifier("damage_per_tick", ModifierOperation.ADD, 4)
        ));

        assertTrue(poison.isHarmful());
    }

    @Test
    void beneficialWhenPositiveHealPerTick() {
        EffectDefinition regrowth = withModifiers(List.of(
            new EffectModifier("heal_per_tick", ModifierOperation.ADD, 5)
        ));

        assertFalse(regrowth.isHarmful());
    }

    @Test
    void notHarmfulWhenNoModifiers() {
        assertFalse(withModifiers(List.of()).isHarmful());
    }

    @Test
    void controlIsEmptyByDefault() {
        assertTrue(withModifiers(List.of()).control().isEmpty());
    }

    @Test
    void controlIsPreservedWhenSupplied() {
        EffectDefinition rooted = new EffectDefinition(
            EffectId.of("rooted"),
            "Rooted",
            6,
            1,
            EffectStacking.REFRESH,
            List.of(),
            List.of(),
            ControlType.ROOT
        );

        assertEquals(ControlType.ROOT, rooted.control().orElseThrow());
    }
}
