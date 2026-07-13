package io.taanielo.jmud.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DamageTypeTest {

    @Test
    void physicalHasNoResistKeyAndIsNotResistible() {
        assertNull(DamageType.PHYSICAL.resistStatKey());
        assertFalse(DamageType.PHYSICAL.isResistible());
    }

    @Test
    void elementalTypesExposeMatchingResistKey() {
        assertEquals("fire_resist", DamageType.FIRE.resistStatKey());
        assertEquals("cold_resist", DamageType.COLD.resistStatKey());
        assertEquals("poison_resist", DamageType.POISON.resistStatKey());
        assertTrue(DamageType.FIRE.isResistible());
        assertTrue(DamageType.COLD.isResistible());
        assertTrue(DamageType.POISON.isResistible());
    }

    @Test
    void fromStringIsCaseInsensitive() {
        assertEquals(DamageType.FIRE, DamageType.fromString("fire"));
        assertEquals(DamageType.FIRE, DamageType.fromString("FIRE"));
        assertEquals(DamageType.COLD, DamageType.fromString("Cold"));
    }

    @Test
    void fromStringDefaultsToPhysicalWhenNullBlankOrUnknown() {
        assertEquals(DamageType.PHYSICAL, DamageType.fromString(null));
        assertEquals(DamageType.PHYSICAL, DamageType.fromString(""));
        assertEquals(DamageType.PHYSICAL, DamageType.fromString("  "));
        assertEquals(DamageType.PHYSICAL, DamageType.fromString("sonic"));
    }
}
