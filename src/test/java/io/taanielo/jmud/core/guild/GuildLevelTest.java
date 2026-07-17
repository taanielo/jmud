package io.taanielo.jmud.core.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class GuildLevelTest {

    @Test
    void freshGuildIsLevelOneWithBaseVault() {
        assertEquals(GuildLevel.ONE, GuildLevel.forLifetimeGold(0));
        assertEquals(1, GuildLevel.ONE.rank());
        assertEquals(40, GuildLevel.ONE.vaultCapacity());
    }

    @Test
    void lifetimeGoldMapsToThresholdedLevels() {
        assertEquals(GuildLevel.ONE, GuildLevel.forLifetimeGold(499));
        assertEquals(GuildLevel.TWO, GuildLevel.forLifetimeGold(500));
        assertEquals(GuildLevel.TWO, GuildLevel.forLifetimeGold(1_999));
        assertEquals(GuildLevel.THREE, GuildLevel.forLifetimeGold(2_000));
        assertEquals(GuildLevel.FOUR, GuildLevel.forLifetimeGold(5_000));
        assertEquals(GuildLevel.FOUR, GuildLevel.forLifetimeGold(14_999));
        assertEquals(GuildLevel.FIVE, GuildLevel.forLifetimeGold(15_000));
        assertEquals(GuildLevel.FIVE, GuildLevel.forLifetimeGold(1_000_000));
    }

    @Test
    void vaultCapacityGrowsTenPerLevelToEighty() {
        assertEquals(40, GuildLevel.ONE.vaultCapacity());
        assertEquals(50, GuildLevel.TWO.vaultCapacity());
        assertEquals(60, GuildLevel.THREE.vaultCapacity());
        assertEquals(70, GuildLevel.FOUR.vaultCapacity());
        assertEquals(80, GuildLevel.FIVE.vaultCapacity());
    }

    @Test
    void nextReturnsFollowingLevelUntilMax() {
        assertEquals(Optional.of(GuildLevel.TWO), GuildLevel.ONE.next());
        assertEquals(Optional.of(GuildLevel.FIVE), GuildLevel.FOUR.next());
        assertEquals(Optional.empty(), GuildLevel.FIVE.next());
    }

    @Test
    void onlyTopLevelIsMax() {
        assertFalse(GuildLevel.ONE.isMax());
        assertFalse(GuildLevel.FOUR.isMax());
        assertTrue(GuildLevel.FIVE.isMax());
    }
}
