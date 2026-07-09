package io.taanielo.jmud.core.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WeatherTest {

    @Test
    void clearWeatherIsInactiveAndHasNoEffect() {
        Weather clear = Weather.clear();

        assertEquals(WeatherType.CLEAR, clear.type());
        assertEquals(0, clear.intensity());
        assertFalse(clear.isActive());
        assertTrue(clear.roomDescription().isEmpty());
        assertFalse(clear.affectsVisibility());
        assertTrue(clear.visibilityStatus().isEmpty());
        assertEquals(0, clear.combatHitModifier());
        assertEquals(0, clear.rangedHitModifier());
    }

    @Test
    void intensityIsClampedToRange() {
        assertEquals(100, new Weather(WeatherType.RAIN, 250).intensity());
        assertEquals(0, new Weather(WeatherType.FOG, -20).intensity());
    }

    @Test
    void clearTypeForcesZeroIntensity() {
        assertEquals(0, new Weather(WeatherType.CLEAR, 80).intensity());
    }

    @Test
    void rainAppliesAccuracyPenaltyAndDodgeBonus() {
        Weather rain = new Weather(WeatherType.RAIN, 50);

        assertTrue(rain.isActive());
        assertEquals(-2, rain.accuracyModifier());
        assertEquals(2, rain.dodgeModifier());
        // Net hit-chance delta = accuracy penalty minus dodge bonus.
        assertEquals(-4, rain.combatHitModifier());
        assertEquals(0, rain.rangedHitModifier());
        assertFalse(rain.affectsVisibility());
    }

    @Test
    void fogReducesVisibilityAndAccuracy() {
        Weather fog = new Weather(WeatherType.FOG, 50);

        assertEquals(-5, fog.accuracyModifier());
        assertEquals(0, fog.dodgeModifier());
        assertEquals(-5, fog.combatHitModifier());
        assertTrue(fog.affectsVisibility());
        assertTrue(fog.visibilityStatus().isPresent());
    }

    @Test
    void stormReducesVisibilityAndAddsRangedMissChance() {
        Weather storm = new Weather(WeatherType.STORM, 80);

        assertEquals(-10, storm.accuracyModifier());
        assertEquals(-10, storm.combatHitModifier());
        assertEquals(-15, storm.rangedHitModifier());
        assertTrue(storm.affectsVisibility());
        assertTrue(storm.visibilityStatus().isPresent());
    }

    @Test
    void descriptionWordingScalesWithIntensity() {
        assertTrue(new Weather(WeatherType.RAIN, 10).roomDescription().orElseThrow().contains("light"));
        assertTrue(new Weather(WeatherType.RAIN, 90).roomDescription().orElseThrow().contains("Heavy"));
    }

    @Test
    void zeroIntensityNonClearTypeIsInactive() {
        Weather ramping = new Weather(WeatherType.RAIN, 0);

        assertFalse(ramping.isActive());
        assertTrue(ramping.roomDescription().isEmpty());
        assertEquals(0, ramping.combatHitModifier());
    }
}
