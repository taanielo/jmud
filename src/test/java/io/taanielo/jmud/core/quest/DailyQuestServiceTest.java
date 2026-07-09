package io.taanielo.jmud.core.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.prompt.PromptSettings;

/**
 * Unit tests for {@link DailyQuestService} covering pool rotation and completion.
 */
class DailyQuestServiceTest {

    private static QuestTemplate variant(String id, String mob, int kills, int gold, int xp, String poolId) {
        return new QuestTemplate(QuestId.of(id), id, "Kill " + kills + " " + mob, mob, kills, gold, xp, poolId);
    }

    private DailyQuestService service;
    private Player basePlayer;

    @BeforeEach
    void setUp() {
        DailyQuestPool slayer = new DailyQuestPool("slayer", "Daily Slayer", List.of(
            variant("slayer-a", "rat", 5, 50, 100, "slayer"),
            variant("slayer-b", "goblin", 6, 75, 150, "slayer"),
            variant("slayer-c", "wolf", 4, 60, 120, "slayer")
        ));
        DailyQuestPool hunter = new DailyQuestPool("hunter", "Daily Hunter", List.of(
            variant("hunter-a", "kobold", 5, 55, 110, "hunter"),
            variant("hunter-b", "spider", 5, 65, 130, "hunter")
        ));
        service = new DailyQuestService(List.of(slayer, hunter));
        User user = new User(Username.of("tester"), Password.of("pass"));
        basePlayer = Player.of(user, PromptSettings.defaultFormat());
    }

    @Test
    void activeDailyQuestStartsAtFirstVariant() {
        assertEquals("slayer-a", service.getActiveDailyQuest("slayer").orElseThrow().id().getValue());
        assertEquals("hunter-a", service.getActiveDailyQuest("hunter").orElseThrow().id().getValue());
    }

    @Test
    void rotateAdvancesEachPoolToNextVariant() {
        service.rotate();
        assertEquals("slayer-b", service.getActiveDailyQuest("slayer").orElseThrow().id().getValue());
        assertEquals("hunter-b", service.getActiveDailyQuest("hunter").orElseThrow().id().getValue());
    }

    @Test
    void rotationWrapsAroundPerPool() {
        // hunter has 2 variants, slayer has 3 — after 2 rotations hunter wraps, slayer does not.
        service.rotate();
        service.rotate();
        assertEquals("slayer-c", service.getActiveDailyQuest("slayer").orElseThrow().id().getValue());
        assertEquals("hunter-a", service.getActiveDailyQuest("hunter").orElseThrow().id().getValue());
        // A third rotation wraps slayer back to the start.
        service.rotate();
        assertEquals("slayer-a", service.getActiveDailyQuest("slayer").orElseThrow().id().getValue());
        assertEquals("hunter-b", service.getActiveDailyQuest("hunter").orElseThrow().id().getValue());
    }

    @Test
    void rotateReturnsNewlyActiveQuests() {
        List<QuestTemplate> active = service.rotate();
        assertEquals(2, active.size());
        assertTrue(active.stream().anyMatch(q -> q.id().getValue().equals("slayer-b")));
        assertTrue(active.stream().anyMatch(q -> q.id().getValue().equals("hunter-b")));
    }

    @Test
    void unknownPoolReturnsEmpty() {
        assertTrue(service.getActiveDailyQuest("nope").isEmpty());
    }

    @Test
    void findQuestByIdResolvesAnyVariantEvenWhenNotActive() {
        assertTrue(service.findQuestById(QuestId.of("slayer-c")).isPresent());
        assertTrue(service.findQuestById(QuestId.of("hunter-b")).isPresent());
        assertTrue(service.findQuestById(QuestId.of("missing")).isEmpty());
    }

    @Test
    void poolIdsExposesAllPools() {
        assertEquals(2, service.poolIds().size());
        assertTrue(service.poolIds().contains("slayer"));
        assertTrue(service.poolIds().contains("hunter"));
    }

    @Test
    void completeGrantsRewardAndClearsActiveQuest() {
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(QuestId.of("slayer-a"), 0));
        DailyQuestCompletionResult result = service.completeDailyQuest(withQuest, QuestId.of("slayer-a"));

        assertTrue(result.success());
        assertNull(result.player().getActiveQuest());
        assertEquals(basePlayer.getGold() + 50, result.player().getGold());
    }

    @Test
    void completeFailsWhenNotOnThatQuest() {
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(QuestId.of("slayer-a"), 0));
        DailyQuestCompletionResult result = service.completeDailyQuest(withQuest, QuestId.of("slayer-b"));

        assertFalse(result.success());
        assertEquals(withQuest.getGold(), result.player().getGold());
    }

    @Test
    void completeFailsWhenQuestNotComplete() {
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(QuestId.of("slayer-a"), 3));
        DailyQuestCompletionResult result = service.completeDailyQuest(withQuest, QuestId.of("slayer-a"));

        assertFalse(result.success());
        assertFalse(result.messages().isEmpty());
    }

    @Test
    void completeFailsForUnknownQuestId() {
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(QuestId.of("slayer-a"), 0));
        DailyQuestCompletionResult result = service.completeDailyQuest(withQuest, QuestId.of("missing"));

        assertFalse(result.success());
    }

    @Test
    void duplicatePoolIdRejected() {
        DailyQuestPool a = new DailyQuestPool("dup", "A", List.of(variant("x", "rat", 1, 1, 1, "dup")));
        DailyQuestPool b = new DailyQuestPool("dup", "B", List.of(variant("y", "rat", 1, 1, 1, "dup")));
        try {
            new DailyQuestService(List.of(a, b));
            org.junit.jupiter.api.Assertions.fail("Expected duplicate pool id to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("dup"));
        }
    }
}
