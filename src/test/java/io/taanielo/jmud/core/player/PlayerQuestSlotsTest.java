package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.prompt.PromptSettings;
import io.taanielo.jmud.core.quest.ActiveQuest;
import io.taanielo.jmud.core.quest.QuestId;

/**
 * Unit tests proving the player's story-quest slot and daily-quest slot are held independently, so
 * a player may carry one of each at once and clearing one leaves the other untouched (issue #599).
 */
class PlayerQuestSlotsTest {

    private static final ActiveQuest STORY = new ActiveQuest(QuestId.of("rat-catcher"), 5);
    private static final ActiveQuest DAILY = new ActiveQuest(QuestId.of("daily-slayer-rats"), 8);

    private static Player newPlayer() {
        User user = new User(Username.of("tester"), Password.of("pass"));
        return Player.of(user, PromptSettings.defaultFormat());
    }

    @Test
    void bothSlotsStartEmpty() {
        Player player = newPlayer();
        assertNull(player.getActiveQuest());
        assertNull(player.getActiveDailyQuest());
    }

    @Test
    void acceptingStoryAndDailyConcurrentlyKeepsBothSlots() {
        Player player = newPlayer()
            .withActiveQuest(STORY)
            .withActiveDailyQuest(DAILY);

        assertEquals(STORY, player.getActiveQuest());
        assertEquals(DAILY, player.getActiveDailyQuest());
    }

    @Test
    void settingStorySlotDoesNotDisturbDailySlot() {
        Player player = newPlayer().withActiveDailyQuest(DAILY);

        Player withStory = player.withActiveQuest(STORY);

        assertEquals(STORY, withStory.getActiveQuest());
        assertEquals(DAILY, withStory.getActiveDailyQuest());
    }

    @Test
    void clearingStorySlotLeavesDailySlotIntact() {
        Player player = newPlayer()
            .withActiveQuest(STORY)
            .withActiveDailyQuest(DAILY);

        Player cleared = player.withActiveQuest(null);

        assertNull(cleared.getActiveQuest());
        assertEquals(DAILY, cleared.getActiveDailyQuest());
    }

    @Test
    void clearingDailySlotLeavesStorySlotIntact() {
        Player player = newPlayer()
            .withActiveQuest(STORY)
            .withActiveDailyQuest(DAILY);

        Player cleared = player.withActiveDailyQuest(null);

        assertEquals(STORY, cleared.getActiveQuest());
        assertNull(cleared.getActiveDailyQuest());
    }
}
