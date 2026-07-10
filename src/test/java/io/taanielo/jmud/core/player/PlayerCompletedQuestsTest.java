package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.quest.QuestId;

class PlayerCompletedQuestsTest {

    @Test
    void emptyHasNoCompletedQuests() {
        PlayerCompletedQuests completed = PlayerCompletedQuests.empty();

        assertEquals(0, completed.count());
        assertFalse(completed.hasCompleted(QuestId.of("bandit-captain-fall")));
    }

    @Test
    void withCompletedAddsQuest() {
        PlayerCompletedQuests completed =
            PlayerCompletedQuests.empty().withCompleted(QuestId.of("bandit-captain-fall"));

        assertTrue(completed.hasCompleted(QuestId.of("bandit-captain-fall")));
        assertEquals(1, completed.count());
    }

    @Test
    void completingSameQuestTwiceIsIdempotentAndReturnsSameInstance() {
        PlayerCompletedQuests first =
            PlayerCompletedQuests.empty().withCompleted(QuestId.of("bandit-captain-fall"));
        PlayerCompletedQuests second = first.withCompleted(QuestId.of("bandit-captain-fall"));

        assertSame(first, second, "re-completing a known quest should not allocate a new component");
        assertEquals(1, second.count());
    }

    @Test
    void preservesFirstCompletedOrder() {
        PlayerCompletedQuests completed = PlayerCompletedQuests.empty()
            .withCompleted(QuestId.of("a"))
            .withCompleted(QuestId.of("b"))
            .withCompleted(QuestId.of("c"));

        assertEquals(List.of("a", "b", "c"), completed.toIdList());
    }

    @Test
    void constructorIgnoresBlanksAndDeduplicates() {
        PlayerCompletedQuests completed =
            new PlayerCompletedQuests(List.of("a", "a", "", "  ", "b"));

        assertEquals(List.of("a", "b"), completed.toIdList());
    }

    @Test
    void nullListYieldsEmpty() {
        PlayerCompletedQuests completed = new PlayerCompletedQuests(null);

        assertEquals(0, completed.count());
    }
}
