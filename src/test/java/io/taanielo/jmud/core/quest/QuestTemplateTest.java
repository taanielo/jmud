package io.taanielo.jmud.core.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link QuestTemplate} item-reward fields and validation.
 */
class QuestTemplateTest {

    private static final QuestTemplate KILL_QUEST =
        new QuestTemplate(QuestId.of("rat-catcher"), "Rat Catcher", "Kill rats.", "rat", 5, 30, 75);

    @Test
    void hasNoItemRewardByDefault() {
        assertFalse(KILL_QUEST.hasItemReward());
        assertEquals(0, KILL_QUEST.itemRewardQuantity());
    }

    @Test
    void withItemRewardAttachesReward() {
        QuestTemplate quest = KILL_QUEST.withItemReward("troll-tooth-charm", 2);
        assertTrue(quest.hasItemReward());
        assertEquals("troll-tooth-charm", quest.itemReward());
        assertEquals(2, quest.itemRewardQuantity());
    }

    @Test
    void rejectsPositiveQuantityWithoutItemId() {
        assertThrows(IllegalArgumentException.class, () -> KILL_QUEST.withItemReward(null, 2));
    }

    @Test
    void rejectsItemIdWithoutPositiveQuantity() {
        assertThrows(IllegalArgumentException.class, () -> KILL_QUEST.withItemReward("troll-tooth-charm", 0));
    }

    @Test
    void hasNoReputationRewardByDefault() {
        assertFalse(KILL_QUEST.hasReputationReward());
        assertEquals(0, KILL_QUEST.reputationRewardDelta());
    }

    @Test
    void withReputationRewardAttachesReward() {
        QuestTemplate quest = KILL_QUEST.withReputationReward("bandits", -25);
        assertTrue(quest.hasReputationReward());
        assertEquals("bandits", quest.reputationRewardFactionId());
        assertEquals(-25, quest.reputationRewardDelta());
    }

    @Test
    void rejectsFactionIdWithZeroDelta() {
        assertThrows(IllegalArgumentException.class, () -> KILL_QUEST.withReputationReward("bandits", 0));
    }

    @Test
    void rejectsNonZeroDeltaWithoutFactionId() {
        assertThrows(IllegalArgumentException.class, () -> KILL_QUEST.withReputationReward(null, -25));
    }

    @Test
    void isRepeatableByDefault() {
        assertTrue(KILL_QUEST.isRepeatable());
        assertTrue(KILL_QUEST.repeatable());
    }

    @Test
    void hasNoPrerequisiteByDefault() {
        assertFalse(KILL_QUEST.hasPrerequisite());
        assertEquals(null, KILL_QUEST.prerequisiteQuestId());
    }

    @Test
    void oneTimeQuestWithPrerequisiteExposesBothFields() {
        QuestTemplate quest = new QuestTemplate(
            QuestId.of("bandit-captain-fall"),
            "The Captain's Fall",
            "Finish the captain.",
            "bandit-captain",
            1,
            200,
            500,
            null,
            0,
            "Bandit's Bane",
            null,
            null,
            null,
            null,
            java.util.List.of(),
            null,
            null,
            0,
            "bandits",
            -40,
            false,
            "bandit-hunter");

        assertFalse(quest.isRepeatable());
        assertTrue(quest.hasPrerequisite());
        assertEquals("bandit-hunter", quest.prerequisiteQuestId());
    }
}
