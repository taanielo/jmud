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
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.character.repository.ClassRepository;
import io.taanielo.jmud.core.character.repository.RaceRepository;
import io.taanielo.jmud.core.faction.Faction;
import io.taanielo.jmud.core.faction.FactionId;
import io.taanielo.jmud.core.faction.FactionRepository;
import io.taanielo.jmud.core.faction.FactionRepositoryException;
import io.taanielo.jmud.core.faction.ReputationService;
import io.taanielo.jmud.core.player.EncumbranceService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.prompt.PromptSettings;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.ItemRepository;

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
    void grantCompletionRewardRecordsOneTimeQuestAsCompleted() {
        QuestTemplate oneTime = new QuestTemplate(
            QuestId.of("bandit-captain-fall"),
            "The Captain's Fall",
            "Finish the captain.",
            "bandit-captain",
            1,
            200,
            500).withReputationReward("bandits", -40);
        QuestTemplate nonRepeatable = new QuestTemplate(
            oneTime.id(), oneTime.name(), oneTime.description(), oneTime.targetMobId(),
            oneTime.requiredKills(), oneTime.goldReward(), oneTime.xpReward(),
            null, 0, null, null, null, null, null, List.of(), null, null, 0,
            "bandits", -40, false, "bandit-hunter", 0);
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(nonRepeatable.id(), 0));

        QuestKillService.CompletionResult result = service.grantCompletionReward(withQuest, nonRepeatable);

        assertTrue(result.player().completedQuests().hasCompleted(nonRepeatable.id()));
        assertEquals(1, result.player().completedQuests().count());
    }

    @Test
    void grantCompletionRewardDoesNotRecordRepeatableQuest() {
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 0));

        QuestKillService.CompletionResult result = service.grantCompletionReward(withQuest, RAT_CATCHER);

        assertFalse(result.player().completedQuests().hasCompleted(RAT_CATCHER_ID));
        assertEquals(0, result.player().completedQuests().count());
    }

    @Test
    void grantCompletionRewardUnlocksQuestAchievementOnOneTimeQuest() throws AchievementRepositoryException {
        QuestTemplate nonRepeatable = nonRepeatableQuest();
        service.setAchievementService(questMilestoneService());
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(nonRepeatable.id(), 0));

        QuestKillService.CompletionResult result = service.grantCompletionReward(withQuest, nonRepeatable);

        assertTrue(result.player().achievements().has(AchievementId.of("quests_1")));
        assertTrue(result.messages().stream()
            .anyMatch(m -> m.equals("Achievement unlocked: Errand Runner!")),
            "Expected unlock message in: " + result.messages());
    }

    @Test
    void grantCompletionRewardDoesNotUnlockAchievementForRepeatableQuest()
        throws AchievementRepositoryException {
        service.setAchievementService(questMilestoneService());
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 0));

        QuestKillService.CompletionResult result = service.grantCompletionReward(withQuest, RAT_CATCHER);

        assertFalse(result.player().achievements().has(AchievementId.of("quests_1")));
        assertFalse(result.messages().stream().anyMatch(m -> m.startsWith("Achievement unlocked")));
    }

    private static QuestTemplate nonRepeatableQuest() {
        return new QuestTemplate(
            QuestId.of("bandit-captain-fall"), "The Captain's Fall", "Finish the captain.",
            "bandit-captain", 1, 200, 500,
            null, 0, null, null, null, null, null, List.of(), null, null, 0, null, 0, false, null, 0);
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

    @Test
    void grantCompletionRewardDoesNotGrantDuplicateTitle() {
        Player withTitleAlready = basePlayer.grantTitle("Goblin Crusher")
            .withActiveQuest(new ActiveQuest(GOBLIN_THRASHER_ID, 0));
        QuestKillService.CompletionResult result = service.grantCompletionReward(withTitleAlready, GOBLIN_THRASHER);

        assertEquals(1, result.player().titles().earned().size());
        assertFalse(result.messages().stream().anyMatch(m -> m.contains("You have earned the title")));
    }

    // ── Item reward on completion ──────────────────────────────────────────

    @Test
    void grantCompletionRewardGrantsItemIntoInventory() {
        Item charm = item("troll-tooth-charm", "Troll Tooth Charm");
        QuestTemplate quest = RAT_CATCHER.withItemReward("troll-tooth-charm", 1);
        QuestKillService svc = new QuestKillService(
            new StubQuestRepository(List.of(quest)),
            new QuestItemRewardService(itemRepo(charm), encumbrance(false)));
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 0));

        QuestKillService.CompletionResult result = svc.grantCompletionReward(withQuest, quest);

        assertEquals(1, result.player().getInventory().size());
        assertTrue(result.droppedItems().isEmpty());
        assertTrue(result.messages().stream()
            .anyMatch(m -> m.contains("and Troll Tooth Charm.")),
            "Receipt should mention the item reward: " + result.messages());
    }

    @Test
    void grantCompletionRewardDropsItemWhenOverweight() {
        Item charm = item("troll-tooth-charm", "Troll Tooth Charm");
        QuestTemplate quest = RAT_CATCHER.withItemReward("troll-tooth-charm", 1);
        QuestKillService svc = new QuestKillService(
            new StubQuestRepository(List.of(quest)),
            new QuestItemRewardService(itemRepo(charm), encumbrance(true)));
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 0));

        QuestKillService.CompletionResult result = svc.grantCompletionReward(withQuest, quest);

        assertTrue(result.player().getInventory().isEmpty());
        assertEquals(1, result.droppedItems().size());
        assertTrue(result.messages().stream()
            .anyMatch(m -> m.contains("falls to the ground at your feet")));
    }

    @Test
    void grantCompletionRewardWithoutItemRewardIsUnchanged() {
        QuestKillService svc = new QuestKillService(
            new StubQuestRepository(List.of(RAT_CATCHER)),
            new QuestItemRewardService(itemRepo(), encumbrance(false)));
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 0));

        QuestKillService.CompletionResult result = svc.grantCompletionReward(withQuest, RAT_CATCHER);

        assertTrue(result.player().getInventory().isEmpty());
        assertTrue(result.droppedItems().isEmpty());
        assertTrue(result.messages().stream()
            .anyMatch(m -> m.equals("You receive 30 gold and 75 experience.")));
    }

    // ── Reputation reward on completion ────────────────────────────────────

    @Test
    void grantCompletionRewardDocksReputation() throws FactionRepositoryException {
        QuestTemplate quest = RAT_CATCHER.withReputationReward("bandits", -25);
        QuestKillService svc = new QuestKillService(
            new StubQuestRepository(List.of(quest)), null, reputationRewardService());
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 0));

        QuestKillService.CompletionResult result = svc.grantCompletionReward(withQuest, quest);

        assertEquals(-25, result.player().reputation().standing(FactionId.of("bandits")));
        assertTrue(result.messages().stream()
                .anyMatch(m -> m.equals("Your standing with the Bandit Brotherhood has fallen.")),
            "Receipt should mention the reputation change: " + result.messages());
    }

    @Test
    void grantCompletionRewardWithoutReputationRewardLeavesStandingUnchanged()
            throws FactionRepositoryException {
        QuestKillService svc = new QuestKillService(
            new StubQuestRepository(List.of(RAT_CATCHER)), null, reputationRewardService());
        Player withQuest = basePlayer.withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 0));

        QuestKillService.CompletionResult result = svc.grantCompletionReward(withQuest, RAT_CATCHER);

        assertTrue(result.player().reputation().isEmpty());
        assertFalse(result.messages().stream().anyMatch(m -> m.contains("Your standing with")));
    }

    // ── Independent story + daily slots ────────────────────────────────────

    @Test
    void killProgressesBothStoryAndDailySlotsIndependently() {
        QuestTemplate dailyRats = new QuestTemplate(
            QuestId.of("daily-rats"), "Daily Rats", "Kill 4 rats.", "rat", 4, 40, 90, "slayer");
        QuestKillService svc = new QuestKillService(new StubQuestRepository(List.of(RAT_CATCHER, dailyRats)));

        Player player = basePlayer
            .withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 5))
            .withActiveDailyQuest(new ActiveQuest(QuestId.of("daily-rats"), 4));

        Optional<QuestKillService.KillResult> result = svc.recordKill(player, "rat");

        assertTrue(result.isPresent());
        Player updated = result.get().player();
        // Each slot decrements by exactly one — no double counting, neither slot ignored.
        assertEquals(4, updated.getActiveQuest().killsRemaining());
        assertEquals(3, updated.getActiveDailyQuest().killsRemaining());
        // One progress message per slot.
        assertEquals(2, result.get().messages().size());
    }

    @Test
    void killMatchingOnlyDailySlotLeavesStorySlotUntouched() {
        QuestTemplate dailyGoblins = new QuestTemplate(
            QuestId.of("daily-goblins"), "Daily Goblins", "Kill 3 goblins.", "goblin", 3, 40, 90, "slayer");
        QuestKillService svc = new QuestKillService(new StubQuestRepository(List.of(RAT_CATCHER, dailyGoblins)));

        Player player = basePlayer
            .withActiveQuest(new ActiveQuest(RAT_CATCHER_ID, 5))
            .withActiveDailyQuest(new ActiveQuest(QuestId.of("daily-goblins"), 3));

        Optional<QuestKillService.KillResult> result = svc.recordKill(player, "goblin");

        assertTrue(result.isPresent());
        Player updated = result.get().player();
        assertEquals(5, updated.getActiveQuest().killsRemaining());
        assertEquals(2, updated.getActiveDailyQuest().killsRemaining());
        assertEquals(1, result.get().messages().size());
    }

    // ── fakes ──────────────────────────────────────────────────────────────

    private static QuestReputationRewardService reputationRewardService() throws FactionRepositoryException {
        Faction bandits = new Faction(
            FactionId.of("bandits"), "the Bandit Brotherhood", "Cutthroats.", -10, 0, 0.02);
        FactionRepository repo = new FactionRepository() {
            @Override
            public List<Faction> findAll() {
                return List.of(bandits);
            }

            @Override
            public Optional<Faction> findById(FactionId factionId) {
                return factionId.equals(bandits.id()) ? Optional.of(bandits) : Optional.empty();
            }
        };
        return new QuestReputationRewardService(new ReputationService(repo));
    }

    private static Item item(String id, String name) {
        return Item.builder(ItemId.of(id), name, "A " + name + ".", ItemAttributes.empty())
            .weight(1)
            .build();
    }

    private static ItemRepository itemRepo(Item... items) {
        return new ItemRepository() {
            @Override
            public void save(Item item) {
            }

            @Override
            public Optional<Item> findById(ItemId id) {
                for (Item item : items) {
                    if (item.getId().equals(id)) {
                        return Optional.of(item);
                    }
                }
                return Optional.empty();
            }
        };
    }

    private static EncumbranceService encumbrance(boolean overburdened) {
        RaceRepository races = new RaceRepository() {
            @Override
            public Optional<Race> findById(RaceId id) {
                return Optional.empty();
            }

            @Override
            public List<Race> findAll() {
                return List.of();
            }
        };
        ClassRepository classes = new ClassRepository() {
            @Override
            public Optional<ClassDefinition> findById(ClassId id) {
                return Optional.empty();
            }

            @Override
            public List<ClassDefinition> findAll() {
                return List.of();
            }
        };
        return new EncumbranceService(races, classes) {
            @Override
            public boolean isOverburdened(Player player) {
                return overburdened;
            }
        };
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
