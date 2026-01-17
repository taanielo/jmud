package io.taanielo.jmud.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BasicStatsTest {

    @Test
    void damageAndHealRespectBounds() {
        Stats stats = BasicStats.builder()
            .hp(10)
            .maxHp(10)
            .mana(5)
            .maxMana(5)
            .strength(2)
            .agility(3)
            .build();
        Stats damaged = stats.damage(7);
        assertEquals(3, damaged.hp());

        Stats healed = damaged.heal(20);
        assertEquals(10, healed.hp());
    }

    @Test
    void manaConsumptionAndRestoreRespectBounds() {
        Stats stats = BasicStats.builder()
            .hp(10)
            .maxHp(10)
            .mana(5)
            .maxMana(5)
            .strength(2)
            .agility(3)
            .build();
        Stats drained = stats.consumeMana(4);
        assertEquals(1, drained.mana());

        Stats restored = drained.restoreMana(10);
        assertEquals(5, restored.mana());
    }

    @Test
    void rejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> BasicStats.builder().hp(-1).maxHp(10).build());
        assertThrows(IllegalArgumentException.class, () -> BasicStats.builder().hp(1).maxHp(0).build());
        assertThrows(IllegalArgumentException.class, () -> BasicStats.builder().hp(0).maxHp(10).mana(-1).build());
        assertThrows(IllegalArgumentException.class, () -> BasicStats.builder().hp(0).maxHp(10).mana(0).maxMana(-1).build());
        assertThrows(IllegalArgumentException.class, () -> BasicStats.builder().hp(0).maxHp(10).strength(-1).build());
        assertThrows(IllegalArgumentException.class, () -> BasicStats.builder().hp(0).maxHp(10).agility(-1).build());
    }

    @Test
    void rejectsNegativeMutations() {
        Stats stats = BasicStats.builder()
            .hp(5)
            .maxHp(5)
            .mana(5)
            .maxMana(5)
            .strength(1)
            .agility(1)
            .build();
        assertThrows(IllegalArgumentException.class, () -> stats.damage(-1));
        assertThrows(IllegalArgumentException.class, () -> stats.heal(-1));
        assertThrows(IllegalArgumentException.class, () -> stats.consumeMana(-1));
        assertThrows(IllegalArgumentException.class, () -> stats.restoreMana(-1));
    }
}
