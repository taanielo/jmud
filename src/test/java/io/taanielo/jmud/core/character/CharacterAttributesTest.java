package io.taanielo.jmud.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CharacterAttributesTest {

    @Test
    void baselineHasAllTensAndZeroModifiers() {
        CharacterAttributes baseline = CharacterAttributes.baseline();
        assertEquals(10, baseline.strength());
        assertEquals(10, baseline.intellect());
        assertEquals(10, baseline.wisdom());
        assertEquals(10, baseline.agility());
        assertEquals(0, baseline.strengthModifier());
        assertEquals(0, baseline.intellectModifier());
        assertEquals(0, baseline.wisdomModifier());
        assertEquals(0, baseline.agilityModifier());
    }

    @Test
    void modifiersAreValueMinusBaseline() {
        CharacterAttributes attributes = new CharacterAttributes(16, 8, 12, 5);
        assertEquals(6, attributes.strengthModifier());
        assertEquals(-2, attributes.intellectModifier());
        assertEquals(2, attributes.wisdomModifier());
        assertEquals(-5, attributes.agilityModifier());
    }

    @Test
    void plusAppliesSignedBonusComponentWise() {
        CharacterAttributes result = CharacterAttributes.baseline()
            .plus(new AttributeBonus(3, -2, 0, 2));
        assertEquals(13, result.strength());
        assertEquals(8, result.intellect());
        assertEquals(10, result.wisdom());
        assertEquals(12, result.agility());
    }
}
