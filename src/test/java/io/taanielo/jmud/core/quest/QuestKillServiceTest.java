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
