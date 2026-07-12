package io.taanielo.jmud.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AttributeGainScheduleTest {

    @Test
    void everyLevelGrantsOnePointPerLevelPastOne() {
        assertEquals(0, AttributeGainCadence.EVERY_LEVEL.gainAtLevel(1));
        assertEquals(1, AttributeGainCadence.EVERY_LEVEL.gainAtLevel(2));
        assertEquals(9, AttributeGainCadence.EVERY_LEVEL.gainAtLevel(10));
    }

    @Test
    void everyThreeLevelsGrantsOnePointPerThreeLevels() {
        assertEquals(0, AttributeGainCadence.EVERY_3_LEVELS.gainAtLevel(1));
        assertEquals(0, AttributeGainCadence.EVERY_3_LEVELS.gainAtLevel(3));
        assertEquals(1, AttributeGainCadence.EVERY_3_LEVELS.gainAtLevel(4));
        assertEquals(3, AttributeGainCadence.EVERY_3_LEVELS.gainAtLevel(10));
    }

    @Test
    void noneNeverGrows() {
        assertEquals(0, AttributeGainCadence.NONE.gainAtLevel(100));
    }

    @Test
    void fromStringMapsKnownCadencesAndDefaultsToNone() {
        assertEquals(AttributeGainCadence.EVERY_LEVEL, AttributeGainCadence.fromString("every_level"));
        assertEquals(AttributeGainCadence.EVERY_2_LEVELS, AttributeGainCadence.fromString("EVERY_2_LEVELS"));
        assertEquals(AttributeGainCadence.NONE, AttributeGainCadence.fromString(null));
        assertEquals(AttributeGainCadence.NONE, AttributeGainCadence.fromString("nonsense"));
    }

    @Test
    void scheduleAccumulatesEachAttributeIndependently() {
        AttributeGainSchedule schedule = new AttributeGainSchedule(
            AttributeGainCadence.EVERY_LEVEL,
            AttributeGainCadence.NONE,
            AttributeGainCadence.NONE,
            AttributeGainCadence.EVERY_3_LEVELS);
        AttributeBonus atTen = schedule.gainsAtLevel(10);
        assertEquals(9, atTen.strength());
        assertEquals(0, atTen.intellect());
        assertEquals(0, atTen.wisdom());
        assertEquals(3, atTen.agility());
    }
}
