package io.taanielo.jmud.core.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.prompt.PromptSettings;

/**
 * Unit tests for {@link QuestKillService}.
 */
class QuestKillServiceTest {

    private static final QuestId RAT_CATCHER_ID = QuestId.of("rat-catcher");
    private static final QuestTemplate RAT_CATCHER = new QuestTemplate(
        RAT_CATCHER_ID,
        "Rat Catcher",
        "Kill 5 rats.",
        "rat",
        5,
        30,
        75
    );
    private static final QuestId GOBLIN_THRASHER_ID = QuestId.of("goblin-thrasher");
    private static final QuestTemplate GOBLIN_THRASHER = new QuestTemplate(
        GOBLIN_THRASHER_ID,
        "Goblin Thrasher",
        "Kill 5 goblins.",
        "goblin",
        5,
        60,
        150,
        null,
        0,
        "Goblin Crusher"
    );

    private QuestKillService service;
    private Player basePlayer;

    @BeforeEach
    void setUp() {
        QuestRepository repo = new StubQuestRepository(List.of(RAT_CATCHER));
        service = new QuestKillService(repo);
        User user = new User(Username.of("tester"), Password.of("pass"));
        basePlayer = Player.of(user, PromptSettings.defaultFormat());
    }

    @Test
    void returnsEmptyWhenNoActiveQuest() {
        Optional<QuestKillService.KillResult> result = service.recordKill(basePlayer, "rat");
        assertTrue(result.isEmpty());
    }

    @Test
    void returnsEmptyWhenMobDoesNotMatchTarget() {
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 5));
        Optional<QuestKillService.KillResult> result = service.recordKill(withQuest, "goblin");
        assertTrue(result.isEmpty());
    }

    @Test
    void decrementsKillCountOnMatchingMob() {
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 5));
        Optional<QuestKillService.KillResult> result = service.recordKill(withQuest, "rat");

        assertTrue(result.isPresent());
        ActiveQuest updated = result.get().player().getActiveQuest();
        assertNotNull(updated);
        assertEquals(4, updated.killsRemaining());
    }

    @Test
    void progressMessageShowsCorrectCount() {
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 3));
        Optional<QuestKillService.KillResult> result = service.recordKill(withQuest, "rat");

        assertTrue(result.isPresent());
        List<String> messages = result.get().messages();
        assertFalse(messages.isEmpty());
        // 2 remaining after decrement means 3/5 done
        assertEquals("Rat Catcher: 3/5 kills.", messages.getFirst());
    }

    @Test
    void completionMessageWhenKillsReachZero() {
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 1));
        Optional<QuestKillService.KillResult> result = service.recordKill(withQuest, "rat");

        assertTrue(result.isPresent());
        ActiveQuest updated = result.get().player().getActiveQuest();
        assertNotNull(updated);
        assertEquals(0, updated.killsRemaining());
        assertTrue(updated.isComplete());
        String msg = result.get().messages().getFirst();
        assertTrue(msg.contains("Guild Clerk"), "Expected Guild Clerk notification, got: " + msg);
    }

    @Test
    void playerIsUpdatedWithDecrementedQuest() {
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 5));
        Optional<QuestKillService.KillResult> result = service.recordKill(withQuest, "rat");

        assertTrue(result.isPresent());
        Player updated = result.get().player();
        assertNotNull(updated.getActiveQuest());
        assertEquals(4, updated.getActiveQuest().killsRemaining());
        // other player state should be preserved
        assertEquals(basePlayer.getUsername(), updated.getUsername());
        assertEquals(basePlayer.getGold(), updated.getGold());
    }

    // ── Completion reward / title granting ─────────────────────────────────

    @Test
    void grantCompletionRewardGrantsTitleWhenQuestHasOne() {
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(GOBLIN_THRASHER_ID, 0));
        QuestKillService.CompletionResult result = service.grantCompletionReward(withQuest, GOBLIN_THRASHER);

        assertTrue(result.player().titles().has("Goblin Crusher"));
        assertNull(result.player().getActiveQuest());
        assertTrue(result.messages().stream().anyMatch(m -> m.contains("You have earned the title: Goblin Crusher!")));
    }

    @Test
    void grantCompletionRewardDoesNotGrantTitleWhenQuestHasNone() {
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 0));
        QuestKillService.CompletionResult result = service.grantCompletionReward(withQuest, RAT_CATCHER);

        assertTrue(result.player().titles().earned().isEmpty());
        assertFalse(result.messages().stream().anyMatch(m -> m.contains("You have earned the title")));
    }

    @Test
    void grantCompletionRewardDoesNotGrantDuplicateTitle() {
        Player withTitleAlready = basePlayer.grantTitle("Goblin Crusher")
            .withActiveQuest(new ActiveQuest(GOBLIN_THRASHER_ID, 0));
        QuestKillService.CompletionResult result = service.grantCompletionReward(withTitleAlready, GOBLIN_THRASHER);

        assertEquals(1, result.player().titles().earned().size());
        assertFalse(result.messages().stream().anyMatch(m -> m.contains("You have earned the title")));
    }

    // ── Stub ──────────────────────────────────────────────────────────────

    private static class StubQuestRepository implements QuestRepository {
        private final List<QuestTemplate> templates;

        StubQuestRepository(List<QuestTemplate> templates) {
            this.templates = templates;
        }

        @Override
        public List<QuestTemplate> findAll() {
            return templates;
        }

        @Override
        public Optional<QuestTemplate> findById(QuestId id) {
            return templates.stream().filter(t -> t.id().equals(id)).findFirst();
        }
    }
}
