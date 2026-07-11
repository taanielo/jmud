package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RarityTest {

    @Test
    void tierIsAtLeastItself() {
        assertTrue(Rarity.COMMON.isAtLeast(Rarity.COMMON));
        assertTrue(Rarity.RARE.isAtLeast(Rarity.RARE));
    }

    @Test
    void higherTierIsAtLeastLowerTier() {
        assertTrue(Rarity.RARE.isAtLeast(Rarity.UNCOMMON));
        assertTrue(Rarity.RARE.isAtLeast(Rarity.COMMON));
        assertTrue(Rarity.UNCOMMON.isAtLeast(Rarity.COMMON));
    }

    @Test
    void lowerTierIsNotAtLeastHigherTier() {
        assertFalse(Rarity.COMMON.isAtLeast(Rarity.RARE));
        assertFalse(Rarity.COMMON.isAtLeast(Rarity.UNCOMMON));
        assertFalse(Rarity.UNCOMMON.isAtLeast(Rarity.RARE));
    }
}
