package io.taanielo.jmud.core.bank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link VaultUpgradeTier} progression table.
 */
class VaultUpgradeTierTest {

    @Test
    void forRank_clampsBelowZeroToBase() {
        assertEquals(VaultUpgradeTier.BASE, VaultUpgradeTier.forRank(-5));
    }

    @Test
    void forRank_clampsAboveMaxToTopTier() {
        assertEquals(VaultUpgradeTier.TIER_THREE, VaultUpgradeTier.forRank(99));
    }

    @Test
    void forRank_matchesEachKnownRank() {
        assertEquals(VaultUpgradeTier.BASE, VaultUpgradeTier.forRank(0));
        assertEquals(VaultUpgradeTier.TIER_ONE, VaultUpgradeTier.forRank(1));
        assertEquals(VaultUpgradeTier.TIER_TWO, VaultUpgradeTier.forRank(2));
        assertEquals(VaultUpgradeTier.TIER_THREE, VaultUpgradeTier.forRank(3));
    }

    @Test
    void slotBonus_growsByTenPerTier() {
        assertEquals(0, VaultUpgradeTier.BASE.slotBonus());
        assertEquals(10, VaultUpgradeTier.TIER_ONE.slotBonus());
        assertEquals(20, VaultUpgradeTier.TIER_TWO.slotBonus());
        assertEquals(30, VaultUpgradeTier.TIER_THREE.slotBonus());
    }

    @Test
    void upgradeCost_escalatesPerTier() {
        assertTrue(VaultUpgradeTier.TIER_ONE.upgradeCost() < VaultUpgradeTier.TIER_TWO.upgradeCost());
        assertTrue(VaultUpgradeTier.TIER_TWO.upgradeCost() < VaultUpgradeTier.TIER_THREE.upgradeCost());
    }

    @Test
    void topTierIsMaxAndHasNoNext() {
        assertTrue(VaultUpgradeTier.TIER_THREE.isMax());
        assertTrue(VaultUpgradeTier.TIER_THREE.next().isEmpty());
        assertFalse(VaultUpgradeTier.BASE.isMax());
        assertEquals(VaultUpgradeTier.TIER_ONE, VaultUpgradeTier.BASE.next().orElseThrow());
    }
}
