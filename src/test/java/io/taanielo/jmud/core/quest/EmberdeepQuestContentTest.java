package io.taanielo.jmud.core.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.faction.FactionId;
import io.taanielo.jmud.core.faction.ReputationService;
import io.taanielo.jmud.core.faction.repository.json.JsonFactionRepository;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.mob.repository.json.JsonMobTemplateRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.prompt.PromptSettings;
import io.taanielo.jmud.core.quest.QuestKillService.CompletionResult;
import io.taanielo.jmud.core.quest.QuestKillService.KillResult;
import io.taanielo.jmud.core.quest.repository.json.JsonQuestRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Content integration test for the Emberdeep quest chain (issue #417). Loads the real {@code data/}
 * files and verifies that the "Ember Culler" lesser cull quest and its "Pyraxis's Fall" capstone are
 * defined against genuine Emberdeep mobs and factions, that the capstone is gated behind completing
 * the cull quest, and that the accept → kill → complete flow grants the expected rewards — mirroring
 * the Frozen Peaks {@code frostbound-cull} → {@code vharixis-end} pair this content deliberately
 * echoes.
 */
class EmberdeepQuestContentTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final QuestId CULL = QuestId.of("ember-culler");
    private static final QuestId CAPSTONE = QuestId.of("pyraxis-fall");

    private static Player newPlayer() {
        User user = new User(Username.of("delver"), Password.of("pass"));
        return Player.of(user, PromptSettings.defaultFormat());
    }

    @Test
    void cullQuest_targetsARealEmberdeepMob() throws Exception {
        JsonQuestRepository quests = new JsonQuestRepository(DATA_ROOT);
        JsonMobTemplateRepository mobs = new JsonMobTemplateRepository(DATA_ROOT);

        QuestTemplate cull = quests.findById(CULL)
            .orElseThrow(() -> new AssertionError("Ember Culler quest must be defined"));

        assertEquals(QuestType.KILL, cull.type());
        assertTrue(cull.isRepeatable(), "the cull quest should be repeatable");
        assertTrue(cull.requiredKills() > 1, "the cull quest should require a handful of kills");
        assertTrue(cull.goldReward() > 0 && cull.xpReward() > 0, "the cull quest must reward gold and XP");

        MobTemplate target = mob(mobs, cull.targetMobId());
        assertFalse(target.worldBoss(), "the cull quest must target a non-boss mob");
        assertTrue(target.tags().contains("emberdeep"),
            "the cull target must be an Emberdeep mob, was " + cull.targetMobId());
    }

    @Test
    void capstoneQuest_targetsTheWorldBossGatedBehindTheCullWithTitleAndReputation() throws Exception {
        JsonQuestRepository quests = new JsonQuestRepository(DATA_ROOT);
        JsonMobTemplateRepository mobs = new JsonMobTemplateRepository(DATA_ROOT);
        JsonFactionRepository factions = new JsonFactionRepository(DATA_ROOT);

        QuestTemplate capstone = quests.findById(CAPSTONE)
            .orElseThrow(() -> new AssertionError("Pyraxis's Fall quest must be defined"));

        assertEquals(QuestType.KILL, capstone.type());
        assertEquals("ember-wyrm", capstone.targetMobId());
        assertEquals(1, capstone.requiredKills());
        assertFalse(capstone.isRepeatable(), "the capstone must be one-time-only");
        assertTrue(capstone.hasPrerequisite(), "the capstone must be gated behind a prerequisite");
        assertEquals(CULL.getValue(), capstone.prerequisiteQuestId(),
            "the capstone must be gated behind the ember-culler quest");
        assertNotNull(capstone.titleReward(), "the capstone must grant a title");

        MobTemplate boss = mob(mobs, capstone.targetMobId());
        assertTrue(boss.worldBoss(), "the capstone target must be the ember-wyrm world boss");

        assertTrue(capstone.hasReputationReward(), "the capstone should bump a faction");
        assertEquals("militia", capstone.reputationRewardFactionId(),
            "the capstone should reward militia reputation");
        assertTrue(factions.findById(FactionId.of(capstone.reputationRewardFactionId())).isPresent(),
            "the capstone's faction must exist");
        assertTrue(capstone.reputationRewardDelta() > 0, "the capstone reputation bump must be positive");
    }

    @Test
    void capstoneTitle_doesNotCollideWithExistingQuestTitles() throws Exception {
        JsonQuestRepository quests = new JsonQuestRepository(DATA_ROOT);
        QuestTemplate capstone = quests.findById(CAPSTONE).orElseThrow();

        long clashes = quests.findAll().stream()
            .filter(q -> !q.id().equals(CAPSTONE))
            .filter(q -> capstone.titleReward().equals(q.titleReward()))
            .count();
        assertEquals(0, clashes,
            "the capstone title '" + capstone.titleReward() + "' must not collide with another quest title");
    }

    @Test
    void prerequisiteGate_closedUntilTheCullIsCompleted() throws Exception {
        JsonQuestRepository quests = new JsonQuestRepository(DATA_ROOT);
        QuestTemplate capstone = quests.findById(CAPSTONE).orElseThrow();

        Player fresh = newPlayer();
        assertFalse(fresh.completedQuests().hasCompleted(CULL),
            "a fresh player has not completed the cull quest");
        // The QUEST ACCEPT / QUEST LIST gate keys off completedQuests().hasCompleted(prerequisite).
        boolean gatedForFresh = capstone.hasPrerequisite()
            && !fresh.completedQuests().hasCompleted(QuestId.of(capstone.prerequisiteQuestId()));
        assertTrue(gatedForFresh, "the capstone must be gated for a player who has not completed the cull");

        Player veteran = fresh.withCompletedQuest(CULL);
        boolean gatedForVeteran = capstone.hasPrerequisite()
            && !veteran.completedQuests().hasCompleted(QuestId.of(capstone.prerequisiteQuestId()));
        assertFalse(gatedForVeteran, "the gate must open once the cull quest is completed");
    }

    @Test
    void capstone_completesEndToEndGrantingTitleReputationAndCompletion() throws Exception {
        JsonQuestRepository quests = new JsonQuestRepository(DATA_ROOT);
        QuestReputationRewardService reputationRewards =
            new QuestReputationRewardService(new ReputationService(new JsonFactionRepository(DATA_ROOT)));
        QuestKillService killService = new QuestKillService(quests, null, reputationRewards);

        QuestTemplate capstone = quests.findById(CAPSTONE).orElseThrow();

        // Simulate the player having already completed the prerequisite cull quest.
        Player player = newPlayer()
            .withCompletedQuest(CULL)
            .withActiveQuest(new ActiveQuest(CAPSTONE, capstone.requiredKills()));

        KillResult bossKill = killService.recordKill(player, capstone.targetMobId())
            .orElseThrow(() -> new AssertionError("boss kill should be recorded"));
        player = bossKill.player();
        assertTrue(player.getActiveQuest().isComplete(), "one boss kill should complete the capstone");

        int goldBefore = player.getGold();
        CompletionResult reward = killService.grantCompletionReward(player, capstone);
        player = reward.player();

        assertEquals(goldBefore + capstone.goldReward(), player.getGold(),
            "completing the capstone should grant its gold reward");
        assertTrue(player.titles().has(capstone.titleReward()),
            "completing the capstone should grant its title");
        assertTrue(player.reputation().standing(FactionId.of(capstone.reputationRewardFactionId())) > 0,
            "completing the capstone should raise militia standing");
        assertTrue(player.getCompletedQuests().contains(CAPSTONE.getValue()),
            "the one-time capstone should be recorded as completed");
    }

    private static MobTemplate mob(JsonMobTemplateRepository mobs, String id) throws RepositoryException {
        return mobs.findAll().stream()
            .filter(m -> id.equals(m.id().getValue()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("mob " + id + " must be present"));
    }
}
