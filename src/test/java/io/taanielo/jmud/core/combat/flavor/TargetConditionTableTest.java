package io.taanielo.jmud.core.combat.flavor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class TargetConditionTableTest {

    private static TargetConditionTable table() {
        return new TargetConditionTable(List.of(
            new TargetConditionTier(100, 100, "is in perfect condition"),
            new TargetConditionTier(90, 99, "has a few scratches"),
            new TargetConditionTier(75, 89, "has some small wounds and bruises"),
            new TargetConditionTier(50, 74, "has quite a few wounds"),
            new TargetConditionTier(30, 49, "has some big nasty wounds and scratches"),
            new TargetConditionTier(15, 29, "looks pretty hurt"),
            new TargetConditionTier(1, 14, "is in awful condition")));
    }

    /** With a 100-HP max, current HP equals the percentage, so every boundary can be probed directly. */
    private static String conditionAt(int percent) {
        return table().describe(percent, 100);
    }

    @Test
    void resolvesEveryTierBoundaryInclusively() {
        assertEquals("is in perfect condition", conditionAt(100));
        assertEquals("has a few scratches", conditionAt(99));
        assertEquals("has a few scratches", conditionAt(90));
        assertEquals("has some small wounds and bruises", conditionAt(89));
        assertEquals("has some small wounds and bruises", conditionAt(75));
        assertEquals("has quite a few wounds", conditionAt(74));
        assertEquals("has quite a few wounds", conditionAt(50));
        assertEquals("has some big nasty wounds and scratches", conditionAt(49));
        assertEquals("has some big nasty wounds and scratches", conditionAt(30));
        assertEquals("looks pretty hurt", conditionAt(29));
        assertEquals("looks pretty hurt", conditionAt(15));
        assertEquals("is in awful condition", conditionAt(14));
        assertEquals("is in awful condition", conditionAt(1));
    }

    @Test
    void onlyAFullCombatantIsPerfect() {
        assertTrue(table().isPerfect(20, 20));
        assertFalse(table().isPerfect(19, 20));
        // 199/200 floors to 99% — not perfect.
        assertFalse(table().isPerfect(199, 200));
    }

    @Test
    void downedCombatantReadsAsWorstTier() {
        assertEquals("is in awful condition", table().describe(0, 20));
        assertEquals("is in awful condition", table().describe(-5, 20));
    }

    @Test
    void percentageFloorsSoNearlyFullIsNotPerfect() {
        // 15/20 = 75% exactly -> small wounds; 14/20 = 70% -> quite a few wounds.
        assertEquals("has some small wounds and bruises", table().describe(15, 20));
        assertEquals("has quite a few wounds", table().describe(14, 20));
    }

    @Test
    void emptyTableRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TargetConditionTable(List.of()));
    }
}
