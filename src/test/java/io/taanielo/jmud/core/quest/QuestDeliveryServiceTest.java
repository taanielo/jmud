package io.taanielo.jmud.core.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.achievement.Achievement;
import io.taanielo.jmud.core.achievement.AchievementCondition;
import io.taanielo.jmud.core.achievement.AchievementId;
import io.taanielo.jmud.core.achievement.AchievementRepository;
import io.taanielo.jmud.core.achievement.AchievementRepositoryException;
import io.taanielo.jmud.core.achievement.AchievementService;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.prompt.PromptSettings;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;

/**
 * Unit tests for {@link QuestDeliveryService}.
 */
class QuestDeliveryServiceTest {

    private static final QuestId COLLECTOR_ID = QuestId.of("rat-tail-collector");
    private static final QuestTemplate COLLECTOR_QUEST = new QuestTemplate(
        COLLECTOR_ID,
        "Rat Tail Collector",
        "Bring 5 rat tails to the Guild Clerk.",
        null, 0,                  // no kill targets
        40, 100,                  // rewards
        "rat-tail", 5,            // delivery fields
        null                      // no title reward
    );

    private QuestDeliveryService service;
    private Player basePlayer;

    @BeforeEach
    void setUp() {
        QuestRepository repo = new StubQuestRepository(List.of(COLLECTOR_QUEST));
        service = new QuestDeliveryService(repo);
        User user = new User(Username.of("tester"), Password.of("pass"));
        basePlayer = Player.of(user, PromptSettings.defaultFormat());
    }

    // ── checkPickupProgress ────────────────────────────────────────────

    @Test
    void returnsEmptyWhenNoActiveQuest() {
        Player withTail = basePlayer.addItem(ratTail());
        Optional<String> msg = service.checkPickupProgress(withTail, ItemId.of("rat-tail"));
        assertTrue(msg.isEmpty());
    }

    @Test
    void returnsEmptyWhenActiveQuestIsKillType() {
        QuestTemplate killQuest = new QuestTemplate(
            QuestId.of("rat-catcher"), "Rat Catcher", "Kill rats.", "rat", 5, 30, 75);
        QuestRepository repo = new StubQuestRepository(List.of(killQuest));
        QuestDeliveryService svc = new QuestDeliveryService(repo);

        Player withQuest = basePlayer
            .withActiveQuest(new ActiveQuest(QuestId.of("rat-catcher"), 5))
            .addItem(ratTail());
        Optional<String> msg = svc.checkPickupProgress(withQuest, ItemId.of("rat-tail"));
        assertTrue(msg.isEmpty());
    }

    @Test
    void returnsEmptyWhenPickedUpItemDoesNotMatchDropId() {
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(COLLECTOR_ID, 0));
        Item wolfPelt = makeItem("wolf-pelt", "Wolf Pelt");
        Player withPelt = withQuest.addItem(wolfPelt);
        Optional<String> msg = service.checkPickupProgress(withPelt, ItemId.of("wolf-pelt"));
        assertTrue(msg.isEmpty());
    }

    @Test
    void returnsProgressMessageOnFirstPickup() {
        Player withQuest = basePlayer
            .withActiveQuest(new ActiveQuest(COLLECTOR_ID, 0))
            .addItem(ratTail());
        Optional<String> msg = service.checkPickupProgress(withQuest, ItemId.of("rat-tail"));
        assertTrue(msg.isPresent(), "Expected progress message");
        assertTrue(msg.get().contains("1/5"), "Expected '1/5' in: " + msg.get());
        assertTrue(msg.get().contains("collected"), "Expected 'collected' in: " + msg.get());
    }

    @Test
    void returnsProgressMessageReflectsCurrentInventoryCount() {
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(COLLECTOR_ID, 0));
        // Add 3 rat tails
        Player with3 = withQuest.addItem(ratTail()).addItem(ratTail()).addItem(ratTail());
        Optional<String> msg = service.checkPickupProgress(with3, ItemId.of("rat-tail"));
        assertTrue(msg.isPresent());
        assertTrue(msg.get().contains("3/5"), "Expected '3/5' in: " + msg.get());
    }

    // ── deliver ────────────────────────────────────────────────────────

    @Test
    void deliverFailsWhenNoActiveQuest() {
        QuestDeliveryService.DeliverResult result = service.deliver(basePlayer);
        assertFalse(result.success());
        assertFalse(result.messages().isEmpty());
        assertTrue(result.messages().getFirst().toLowerCase(Locale.ROOT).contains("no active"),
            "Expected 'no active' in: " + result.messages());
    }

    @Test
    void deliverFailsWhenActiveQuestIsKillType() {
        QuestTemplate killQuest = new QuestTemplate(
            QuestId.of("rat-catcher"), "Rat Catcher", "Kill rats.", "rat", 5, 30, 75);
        QuestRepository repo = new StubQuestRepository(List.of(killQuest));
        QuestDeliveryService svc = new QuestDeliveryService(repo);

        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(QuestId.of("rat-catcher"), 5));
        QuestDeliveryService.DeliverResult result = svc.deliver(withQuest);
        assertFalse(result.success());
        assertTrue(result.messages().getFirst().contains("QUEST COMPLETE"),
            "Expected 'QUEST COMPLETE' hint in: " + result.messages());
    }

    @Test
    void deliverRejectsIncompleteWithFeedback() {
        // Player has 3/5 rat tails
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(COLLECTOR_ID, 0));
        Player with3 = withQuest.addItem(ratTail()).addItem(ratTail()).addItem(ratTail());
        QuestDeliveryService.DeliverResult result = service.deliver(with3);
        assertFalse(result.success(), "Should fail when fewer than 5 tails held");
        assertNull(result.player(), "No updated player on failure");
        String msg = result.messages().getFirst();
        assertTrue(msg.contains("3/5") || msg.contains("3"), "Expected count in: " + msg);
        assertTrue(msg.contains("2") || msg.contains("still need"), "Expected missing count info in: " + msg);
    }

    @Test
    void deliverSuccessRemovesItemsAndGrantsReward() {
        // Player has exactly 5 rat tails
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(COLLECTOR_ID, 0));
        Player with5 = withQuest;
        for (int i = 0; i < 5; i++) {
            with5 = with5.addItem(ratTail());
        }
        assertEquals(5, countRatTails(with5), "Precondition: 5 tails in inventory");

        QuestDeliveryService.DeliverResult result = service.deliver(with5);

        assertTrue(result.success(), "Delivery should succeed with 5 tails");
        assertNotNull(result.player(), "Updated player required on success");
        Player rewarded = result.player();

        assertEquals(0, countRatTails(rewarded), "All rat tails should be removed after delivery");
        assertNull(rewarded.getActiveQuest(), "Active quest should be cleared after delivery");
        assertEquals(40, rewarded.getGold(), "Gold reward should be granted");
        assertTrue(rewarded.getExperience() >= 100, "XP reward should be granted");
    }

    @Test
    void deliverSuccessWithMoreThanRequiredRemovesExactCount() {
        // Player has 7 rat tails but only 5 are needed
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(COLLECTOR_ID, 0));
        Player with7 = withQuest;
        for (int i = 0; i < 7; i++) {
            with7 = with7.addItem(ratTail());
        }

        QuestDeliveryService.DeliverResult result = service.deliver(with7);

        assertTrue(result.success());
        Player rewarded = result.player();
        assertEquals(2, countRatTails(rewarded),
            "Only 5 tails should be removed; 2 should remain");
    }

    @Test
    void deliverMessagesContainGuildClerkAcknowledgement() {
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(COLLECTOR_ID, 0));
        Player with5 = withQuest;
        for (int i = 0; i < 5; i++) {
            with5 = with5.addItem(ratTail());
        }

        QuestDeliveryService.DeliverResult result = service.deliver(with5);

        assertTrue(result.success());
        boolean hasClerkMsg = result.messages().stream()
            .anyMatch(m -> m.contains("Guild Clerk"));
        assertTrue(hasClerkMsg, "Expected Guild Clerk message in: " + result.messages());
    }

    // ── title granting ───────────────────────────────────────────────────

    @Test
    void deliverGrantsTitleWhenQuestHasOne() {
        QuestId titledId = QuestId.of("titled-collector");
        QuestTemplate titledQuest = new QuestTemplate(
            titledId, "Titled Collector", "Bring 5 rat tails.",
            null, 0, 40, 100, "rat-tail", 5, "Rat Slayer");
        QuestRepository repo = new StubQuestRepository(List.of(titledQuest));
        QuestDeliveryService svc = new QuestDeliveryService(repo);

        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(titledId, 0));
        Player with5 = withQuest;
        for (int i = 0; i < 5; i++) {
            with5 = with5.addItem(ratTail());
        }

        QuestDeliveryService.DeliverResult result = svc.deliver(with5);

        assertTrue(result.success());
        assertTrue(result.player().titles().has("Rat Slayer"));
        assertTrue(result.messages().stream().anyMatch(m -> m.contains("You have earned the title: Rat Slayer!")));
    }

    @Test
    void deliverDoesNotGrantTitleWhenQuestHasNone() {
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(COLLECTOR_ID, 0));
        Player with5 = withQuest;
        for (int i = 0; i < 5; i++) {
            with5 = with5.addItem(ratTail());
        }

        QuestDeliveryService.DeliverResult result = service.deliver(with5);

        assertTrue(result.success());
        assertTrue(result.player().titles().earned().isEmpty());
        assertFalse(result.messages().stream().anyMatch(m -> m.contains("You have earned the title")));
    }

    @Test
    void deliverDoesNotGrantDuplicateTitle() {
        QuestId titledId = QuestId.of("titled-collector");
        QuestTemplate titledQuest = new QuestTemplate(
            titledId, "Titled Collector", "Bring 5 rat tails.",
            null, 0, 40, 100, "rat-tail", 5, "Rat Slayer");
        QuestRepository repo = new StubQuestRepository(List.of(titledQuest));
        QuestDeliveryService svc = new QuestDeliveryService(repo);

        Player withQuest = basePlayer.grantTitle("Rat Slayer")
            .withActiveQuest(new ActiveQuest(titledId, 0));
        Player with5 = withQuest;
        for (int i = 0; i < 5; i++) {
            with5 = with5.addItem(ratTail());
        }

        QuestDeliveryService.DeliverResult result = svc.deliver(with5);

        assertTrue(result.success());
        assertEquals(1, result.player().titles().earned().size());
        assertFalse(result.messages().stream().anyMatch(m -> m.contains("You have earned the title")));
    }

    @Test
    void deliverUnlocksQuestAchievementOnOneTimeQuest() throws AchievementRepositoryException {
        QuestTemplate oneTime = nonRepeatableCollector();
        QuestDeliveryService svc = new QuestDeliveryService(new StubQuestRepository(List.of(oneTime)));
        svc.setAchievementService(questMilestoneService());
        Player with5 = basePlayer.withActiveQuest(new ActiveQuest(oneTime.id(), 0));
        for (int i = 0; i < 5; i++) {
            with5 = with5.addItem(ratTail());
        }

        QuestDeliveryService.DeliverResult result = svc.deliver(with5);

        assertTrue(result.success());
        assertTrue(result.player().achievements().has(AchievementId.of("quests_1")));
        assertTrue(result.messages().stream()
            .anyMatch(m -> m.equals("Achievement unlocked: Errand Runner!")),
            "Expected unlock message in: " + result.messages());
    }

    @Test
    void deliverDoesNotUnlockAchievementForRepeatableQuest() throws AchievementRepositoryException {
        service.setAchievementService(questMilestoneService());
        Player with5 = basePlayer.withActiveQuest(new ActiveQuest(COLLECTOR_ID, 0));
        for (int i = 0; i < 5; i++) {
            with5 = with5.addItem(ratTail());
        }

        QuestDeliveryService.DeliverResult result = service.deliver(with5);

        assertTrue(result.success());
        assertFalse(result.player().achievements().has(AchievementId.of("quests_1")));
        assertFalse(result.messages().stream().anyMatch(m -> m.startsWith("Achievement unlocked")));
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static QuestTemplate nonRepeatableCollector() {
        return new QuestTemplate(
            QuestId.of("rare-relic-collector"), "Rare Relic Collector", "Bring 5 rat tails once.",
            null, 0, 40, 100, "rat-tail", 5, null,
            null, null, null, null, List.of(), null, null, 0, null, 0, false, null, 0);
    }

    private static AchievementService questMilestoneService() throws AchievementRepositoryException {
        AchievementRepository repository = new AchievementRepository() {
            private final Achievement questsOne = new Achievement(
                AchievementId.of("quests_1"), "Errand Runner", "Complete 1 one-time contract.",
                AchievementCondition.QUESTS_COMPLETED, 1);

            @Override
            public List<Achievement> findAll() {
                return List.of(questsOne);
            }

            @Override
            public Optional<Achievement> findById(AchievementId id) {
                return findAll().stream().filter(a -> a.id().equals(id)).findFirst();
            }
        };
        return new AchievementService(repository);
    }

    private static Item ratTail() {
        return makeItem("rat-tail", "Rat Tail");
    }

    private static Item makeItem(String id, String name) {
        return Item.builder(ItemId.of(id), name, "A " + name + ".", ItemAttributes.empty())
            .weight(0)
            .value(0)
            .build();
    }

    private static int countRatTails(Player player) {
        int count = 0;
        for (Item item : player.getInventory()) {
            if ("rat-tail".equals(item.getId().getValue())) {
                count++;
            }
        }
        return count;
    }

    // ── stub repository ────────────────────────────────────────────────

    static class StubQuestRepository implements QuestRepository {
        private final List<QuestTemplate> templates;
        StubQuestRepository(List<QuestTemplate> templates) { this.templates = templates; }
        @Override public List<QuestTemplate> findAll() { return templates; }
        @Override public Optional<QuestTemplate> findById(QuestId id) {
            return templates.stream().filter(t -> t.id().equals(id)).findFirst();
        }
    }
}
