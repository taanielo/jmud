package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.combat.DamageType;

class RoomHazardTest {

    @Test
    void rejectsPhysicalDamageType() {
        assertThrows(IllegalArgumentException.class,
            () -> new RoomHazard(DamageType.PHYSICAL, 1, 5, "It hurts."));
    }

    @Test
    void rejectsNonPositiveMinimum() {
        assertThrows(IllegalArgumentException.class,
            () -> new RoomHazard(DamageType.FIRE, 0, 5, "It burns."));
    }

    @Test
    void rejectsMaximumBelowMinimum() {
        assertThrows(IllegalArgumentException.class,
            () -> new RoomHazard(DamageType.FIRE, 8, 4, "It burns."));
    }

    @Test
    void rejectsBlankMessage() {
        assertThrows(IllegalArgumentException.class,
            () -> new RoomHazard(DamageType.COLD, 1, 5, "  "));
    }

    @Test
    void warningLineNamesElementAndMitigation() {
        RoomHazard hazard = new RoomHazard(DamageType.FIRE, 5, 10, "The heat sears you!");
        String warning = hazard.warningLine();

        assertTrue(warning.contains("(Hazard)"), "warning is clearly marked");
        assertTrue(warning.contains("fire"), "warning names the element");
        assertTrue(warning.contains("resistance"), "warning mentions resistance mitigation");
    }

    @Test
    void acceptsAllResistibleTypes() {
        assertEquals(DamageType.POISON,
            new RoomHazard(DamageType.POISON, 2, 3, "Poison stings.").damageType());
    }
}
