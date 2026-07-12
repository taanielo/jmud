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
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link QuestNpcDeliveryService}: granting an NPC-delivery errand and completing it
 * at the receiver NPC's room.
 */
class QuestNpcDeliveryServiceTest {

    private static final QuestId DISPATCH_ID = QuestId.of("deliver-package");
    private static final RoomId RECEIVER_ROOM = RoomId.of("hunters-clearing");
    private static final QuestTemplate DISPATCH_QUEST = new QuestTemplate(
        DISPATCH_ID,
        "The Quartermaster's Dispatch",
        "Carry the sealed dispatch to Ranger Sella.",
        null, 0,               // no kill targets
        30, 90,                // rewards
        null, 0,               // no item-collection fields
        "Courier",             // title reward
        "quartermaster", "forest-ranger", "hunters-clearing", "sealed-dispatch");

    private QuestNpcDeliveryService service;
    private Player basePlayer;

    @BeforeEach
    void setUp() {
        QuestRepository repo = new StubQuestRepository(List.of(DISPATCH_QUEST));
        service = new QuestNpcDeliveryService(repo);
        User user = new User(Username.of("tester"), Password.of("pass"));
        basePlayer = Player.of(user, PromptSettings.defaultFormat());
    }

    // ── grant ──────────────────────────────────────────────────────────

    @Test
    void grantActivatesQuestAndHandsOverPackage() {
        DeliveryQuestResult result = service.grant(basePlayer, DISPATCH_QUEST, packageItem());

        assertTrue(result.success());
        Player updated = result.player();
        assertNotNull(updated);
        assertNotNull(updated.getActiveQuest());
        assertEquals(DISPATCH_ID, updated.getActiveQuest().templateId());
        assertTrue(holdsPackage(updated), "Player should now carry the package");
    }

    @Test
    void grantFailsWhenPlayerAlreadyHasQuest() {
        Player busy = basePlayer.withActiveQuest(new ActiveQuest(QuestId.of("rat-catcher"), 5));
        DeliveryQuestResult result = service.grant(busy, DISPATCH_QUEST, packageItem());

        assertFalse(result.success());
        assertNull(result.player());
    }

    @Test
    void grantFailsWhenTemplateIsNotNpcDelivery() {
        QuestTemplate killQuest = new QuestTemplate(
            QuestId.of("rat-catcher"), "Rat Catcher", "Kill rats.", "rat", 5, 30, 75);
        DeliveryQuestResult result = service.grant(basePlayer, killQuest, packageItem());

        assertFalse(result.success());
    }

    @Test
    void grantFailsWhenPackageDoesNotMatch() {
        Item wrong = makeItem("wolf-pelt", "a wolf pelt");
        DeliveryQuestResult result = service.grant(basePlayer, DISPATCH_QUEST, wrong);

        assertFalse(result.success());
    }

    // ── deliver ────────────────────────────────────────────────────────

    @Test
    void deliverSucceedsAtReceiverWithPackage() {
        Player carrying = basePlayer
            .withActiveQuest(new ActiveQuest(DISPATCH_ID, 0))
            .addItem(packageItem());

        DeliveryQuestResult result = service.deliver(carrying, RECEIVER_ROOM, true);

        assertTrue(result.success());
        Player rewarded = result.player();
        assertNull(rewarded.getActiveQuest(), "Quest cleared on delivery");
        assertFalse(holdsPackage(rewarded), "Package consumed on delivery");
        assertEquals(30, rewarded.getGold());
        assertTrue(rewarded.getExperience() >= 90);
        assertTrue(rewarded.titles().has("Courier"));
        assertTrue(result.messages().stream().anyMatch(m -> m.contains("Courier")));
    }

    @Test
    void deliverFailsInWrongRoom() {
        Player carrying = basePlayer
            .withActiveQuest(new ActiveQuest(DISPATCH_ID, 0))
            .addItem(packageItem());

        DeliveryQuestResult result = service.deliver(carrying, RoomId.of("armory"), false);

        assertFalse(result.success());
        assertNull(result.player());
    }

    @Test
    void deliverFailsWhenReceiverNotPresent() {
        Player carrying = basePlayer
            .withActiveQuest(new ActiveQuest(DISPATCH_ID, 0))
            .addItem(packageItem());

        DeliveryQuestResult result = service.deliver(carrying, RECEIVER_ROOM, false);

        assertFalse(result.success());
    }

    @Test
    void deliverFailsWhenPackageMissing() {
        Player carrying = basePlayer.withActiveQuest(new ActiveQuest(DISPATCH_ID, 0));

        DeliveryQuestResult result = service.deliver(carrying, RECEIVER_ROOM, true);

        assertFalse(result.success());
    }

    @Test
    void deliverFailsWithNoActiveQuest() {
        DeliveryQuestResult result = service.deliver(basePlayer, RECEIVER_ROOM, true);

        assertFalse(result.success());
    }

    @Test
    void deliverUnlocksQuestAchievementOnOneTimeErrand() throws AchievementRepositoryException {
        QuestTemplate oneTime = nonRepeatableDispatch();
        QuestNpcDeliveryService svc = new QuestNpcDeliveryService(new StubQuestRepository(List.of(oneTime)));
        svc.setAchievementService(questMilestoneService());
        Player carrying = basePlayer
            .withActiveQuest(new ActiveQuest(oneTime.id(), 0))
            .addItem(packageItem());

        DeliveryQuestResult result = svc.deliver(carrying, RECEIVER_ROOM, true);

        assertTrue(result.success());
        assertTrue(result.player().achievements().has(AchievementId.of("quests_1")));
        assertTrue(result.messages().stream()
            .anyMatch(m -> m.equals("Achievement unlocked: Errand Runner!")),
            "Expected unlock message in: " + result.messages());
    }

    @Test
    void deliverDoesNotUnlockAchievementForRepeatableErrand() throws AchievementRepositoryException {
        service.setAchievementService(questMilestoneService());
        Player carrying = basePlayer
            .withActiveQuest(new ActiveQuest(DISPATCH_ID, 0))
            .addItem(packageItem());

        DeliveryQuestResult result = service.deliver(carrying, RECEIVER_ROOM, true);

        assertTrue(result.success());
        assertFalse(result.player().achievements().has(AchievementId.of("quests_1")));
        assertFalse(result.messages().stream().anyMatch(m -> m.startsWith("Achievement unlocked")));
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static QuestTemplate nonRepeatableDispatch() {
        return new QuestTemplate(
            QuestId.of("deliver-relic"), "The Sealed Relic", "Carry the relic to Ranger Sella.",
            null, 0, 30, 90, null, 0, null,
            "quartermaster", "forest-ranger", "hunters-clearing", "sealed-dispatch",
            List.of(), null, null, 0, null, 0, false, null, 0);
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

    private static Item packageItem() {
        return makeItem("sealed-dispatch", "a sealed dispatch");
    }

    private static Item makeItem(String id, String name) {
        return Item.builder(ItemId.of(id), name, "A test item.", ItemAttributes.empty())
            .weight(0)
            .value(0)
            .build();
    }

    private static boolean holdsPackage(Player player) {
        return player.getInventory().stream()
            .anyMatch(i -> "sealed-dispatch".equals(i.getId().getValue()));
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
