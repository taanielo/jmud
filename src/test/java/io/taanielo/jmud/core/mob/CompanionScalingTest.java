package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CompanionScaling}: the deterministic, capped owner-level scaling shared by
 * tamed (TAME) and summoned (SUMMON) companions.
 */
class CompanionScalingTest {

    @Test
    void level1OwnerLeavesCompanionUnscaled() {
        CompanionScaling scaling = CompanionScaling.forOwnerLevel(1);

        assertEquals(1.0, scaling.hpMultiplier(), 1e-9);
        assertEquals(1.0, scaling.damageMultiplier(), 1e-9);
        assertEquals(100, scaling.scaleMaxHp(100));
        assertEquals(20, scaling.scaleDamage(20));
    }

    @Test
    void higherLevelOwnerGetsStrictlyTougherAndHarderHittingCompanion() {
        CompanionScaling low = CompanionScaling.forOwnerLevel(6);
        CompanionScaling high = CompanionScaling.forOwnerLevel(40);

        assertTrue(high.scaleMaxHp(100) > low.scaleMaxHp(100),
            "A higher-level owner's companion has strictly more max HP for the same template");
        assertTrue(high.scaleDamage(20) > low.scaleDamage(20),
            "A higher-level owner's companion deals strictly more damage for the same template");
    }

    @Test
    void scalingIsDeterministic() {
        CompanionScaling first = CompanionScaling.forOwnerLevel(30);
        CompanionScaling second = CompanionScaling.forOwnerLevel(30);

        assertEquals(first, second, "Same owner level yields identical scaling");
        assertEquals(first.scaleMaxHp(100), second.scaleMaxHp(100));
        assertEquals(first.scaleDamage(20), second.scaleDamage(20));
    }

    @Test
    void scalingIsCappedAtVeryHighLevel() {
        CompanionScaling extreme = CompanionScaling.forOwnerLevel(10_000);

        assertEquals(CompanionScaling.MAX_HP_MULTIPLIER, extreme.hpMultiplier(), 1e-9,
            "The HP multiplier never exceeds its hard cap");
        assertEquals(CompanionScaling.MAX_DAMAGE_MULTIPLIER, extreme.damageMultiplier(), 1e-9,
            "The damage multiplier never exceeds its hard cap");
        assertEquals(400, extreme.scaleMaxHp(100));
        assertEquals(60, extreme.scaleDamage(20));
    }

    @Test
    void nonPositiveDamageIsPassedThroughUnchanged() {
        CompanionScaling scaling = CompanionScaling.forOwnerLevel(60);

        assertEquals(0, scaling.scaleDamage(0));
        assertEquals(-5, scaling.scaleDamage(-5));
    }

    @Test
    void ownerLevelBelowBaseIsClampedToUnscaled() {
        CompanionScaling scaling = CompanionScaling.forOwnerLevel(0);

        assertEquals(1.0, scaling.hpMultiplier(), 1e-9);
        assertEquals(1.0, scaling.damageMultiplier(), 1e-9);
    }
}
