package io.taanielo.jmud.core.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link ExplorationQuestService}.
 */
class ExplorationQuestServiceTest {

    private static final QuestId EXPLORE_ID = QuestId.of("explore-catacombs");
    private static final QuestTemplate EXPLORE_QUEST = new QuestTemplate(
        EXPLORE_ID,
        "Into the Catacombs",
        "Chart every chamber of the Catacombs.",
        60,
        150,
        "Catacomb Cartographer",
        List.of("catacombs-entrance", "ossuary-hall", "burial-alcove")
    );

    private ExplorationQuestService service;
    private Player basePlayer;

    @BeforeEach
    void setUp() {
        QuestRepository repo = new StubQuestRepository(List.of(EXPLORE_QUEST));
        service = new ExplorationQuestService(repo);
        User user = new User(Username.of("tester"), Password.of("pass"));
        basePlayer = Player.of(user, PromptSettings.defaultFormat());
    }

    @Test
    void returnsEmptyWhenNoActiveQuest() {
        Optional<ExplorationQuestResult> result =
            service.recordRoomVisit(basePlayer, RoomId.of("catacombs-entrance"));
        assertTrue(result.isEmpty());
    }

    @Test
    void returnsEmptyWhenRoomIsNotRequired() {
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(EXPLORE_ID, 0));
        Optional<ExplorationQuestResult> result =
            service.recordRoomVisit(withQuest, RoomId.of("courtyard"));
        assertTrue(result.isEmpty());
    }

    @Test
    void returnsEmptyWhenRoomAlreadyVisited() {
        Player withQuest = basePlayer.withActiveQuest(
            new ActiveQuest(EXPLORE_ID, 0, List.of("catacombs-entrance")));
        Optional<ExplorationQuestResult> result =
            service.recordRoomVisit(withQuest, RoomId.of("catacombs-entrance"));
        assertTrue(result.isEmpty());
    }

    @Test
    void recordsProgressForNewRequiredRoom() {
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(EXPLORE_ID, 0));

        ExplorationQuestResult result =
            service.recordRoomVisit(withQuest, RoomId.of("catacombs-entrance")).orElseThrow();

        assertFalse(result.completed());
        assertTrue(result.player().getActiveQuest().hasVisited("catacombs-entrance"));
        assertEquals(1, result.player().getActiveQuest().visitedRoomIds().size());
        assertTrue(result.messages().get(0).contains("1 of 3"));
    }

    @Test
    void isCaseInsensitiveOnRoomId() {
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(EXPLORE_ID, 0));

        ExplorationQuestResult result =
            service.recordRoomVisit(withQuest, RoomId.of("Catacombs-Entrance")).orElseThrow();

        assertTrue(result.player().getActiveQuest().hasVisited("catacombs-entrance"));
    }

    @Test
    void completesAndRewardsOnFinalRoom() {
        Player withQuest = basePlayer.withActiveQuest(
            new ActiveQuest(EXPLORE_ID, 0, List.of("catacombs-entrance", "ossuary-hall")));
        long goldBefore = withQuest.getGold();

        ExplorationQuestResult result =
            service.recordRoomVisit(withQuest, RoomId.of("burial-alcove")).orElseThrow();

        assertTrue(result.completed());
        assertNull(result.player().getActiveQuest());
        assertEquals(goldBefore + 60, result.player().getGold());
        assertTrue(result.player().titles().has("Catacomb Cartographer"));
        assertTrue(result.messages().stream().anyMatch(m -> m.contains("Quest complete")));
    }

    @Test
    void returnsEmptyForNonExplorationQuest() {
        QuestTemplate killQuest = new QuestTemplate(
            QuestId.of("rat-catcher"), "Rat Catcher", "Kill rats.", "rat", 5, 30, 75);
        QuestRepository repo = new StubQuestRepository(List.of(killQuest));
        ExplorationQuestService killService = new ExplorationQuestService(repo);
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(QuestId.of("rat-catcher"), 5));

        Optional<ExplorationQuestResult> result =
            killService.recordRoomVisit(withQuest, RoomId.of("catacombs-entrance"));

        assertTrue(result.isEmpty());
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
