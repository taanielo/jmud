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
}
