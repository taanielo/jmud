package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RarityTest {

    @Test
    void tierIsAtLeastItself() {
        assertTrue(Rarity.COMMON.isAtLeast(Rarity.COMMON));
        assertTrue(Rarity.RARE.isAtLeast(Rarity.RARE));
        assertTrue(Rarity.EPIC.isAtLeast(Rarity.EPIC));
    }

    @Test
    void higherTierIsAtLeastLowerTier() {
        assertTrue(Rarity.RARE.isAtLeast(Rarity.UNCOMMON));
        assertTrue(Rarity.RARE.isAtLeast(Rarity.COMMON));
        assertTrue(Rarity.UNCOMMON.isAtLeast(Rarity.COMMON));
    }

    @Test
    void epicOutranksRare() {
        assertTrue(Rarity.EPIC.isAtLeast(Rarity.RARE));
        assertTrue(Rarity.EPIC.isAtLeast(Rarity.UNCOMMON));
        assertTrue(Rarity.EPIC.isAtLeast(Rarity.COMMON));
        assertFalse(Rarity.RARE.isAtLeast(Rarity.EPIC));
    }

    @Test
    void lowerTierIsNotAtLeastHigherTier() {
        assertFalse(Rarity.COMMON.isAtLeast(Rarity.RARE));
        assertFalse(Rarity.COMMON.isAtLeast(Rarity.UNCOMMON));
        assertFalse(Rarity.UNCOMMON.isAtLeast(Rarity.RARE));
    }

    @Test
    void epicRoundTripsThroughId() {
        assertEquals("epic", Rarity.EPIC.id());
        assertEquals(Rarity.EPIC, Rarity.fromId("epic"));
        assertEquals(Rarity.EPIC, Rarity.fromId("  EPIC  "));
    }
}
