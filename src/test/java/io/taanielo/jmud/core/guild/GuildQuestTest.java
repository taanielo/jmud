package io.taanielo.jmud.core.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Unit tests for the immutable {@link GuildQuest} value object and its clamping behaviour. */
class GuildQuestTest {

    private static GuildQuest fresh() {
        return GuildQuest.fromObjective(
            new GuildQuestObjective("q", "Dire Cull", "dire-wolf", "dire wolves", 3, 500, 2));
    }

    @Test
    void fromObjectiveStartsWithNoProgress() {
        GuildQuest quest = fresh();
        assertEquals(0, quest.currentKills());
        assertEquals(3, quest.requiredKills());
        assertFalse(quest.isComplete());
    }

    @Test
    void recordKillAdvancesProgress() {
        GuildQuest quest = fresh().recordKill();
        assertEquals(1, quest.currentKills());
        assertFalse(quest.isComplete());
    }

    @Test
    void isCompleteWhenTargetReached() {
        GuildQuest quest = fresh().recordKill().recordKill().recordKill();
        assertEquals(3, quest.currentKills());
        assertTrue(quest.isComplete());
    }

    @Test
    void progressClampsAtTarget() {
        GuildQuest quest = fresh().recordKill().recordKill().recordKill().recordKill().recordKill();
        assertEquals(3, quest.currentKills(), "currentKills must never exceed requiredKills");
        assertTrue(quest.isComplete());
    }

    @Test
    void progressLineRendersSlayedCount() {
        GuildQuest quest = fresh().recordKill();
        assertEquals("Slayed 1 / 3 dire wolves", quest.progressLine());
    }
}
