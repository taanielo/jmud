package io.taanielo.jmud.core.achievement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.quest.QuestId;

/**
 * Unit tests for {@link AchievementCondition}, focused on the {@code quests_completed} condition
 * added in issue #413.
 */
class AchievementConditionTest {

    private static Player playerWithCompletedQuests(int count) {
        User user = User.of(Username.of("hero"), Password.hash("pw", 1));
        Player player = Player.of(user, "%hp> ");
        for (int i = 0; i < count; i++) {
            player = player.withCompletedQuest(QuestId.of("quest-" + i));
        }
        return player;
    }

    @Test
    void questsCompletedTokenParsesToCondition() {
        assertEquals(AchievementCondition.QUESTS_COMPLETED,
            AchievementCondition.fromToken("quests_completed"));
    }

    @Test
    void questsCompletedReadsCompletedQuestCount() {
        assertEquals(0L, AchievementCondition.QUESTS_COMPLETED.currentValue(playerWithCompletedQuests(0)));
        assertEquals(3L, AchievementCondition.QUESTS_COMPLETED.currentValue(playerWithCompletedQuests(3)));
    }

    @Test
    void questsCompletedRendersQuestsProgressUnit() {
        assertEquals("quests", AchievementCondition.QUESTS_COMPLETED.progressUnit());
    }
}
