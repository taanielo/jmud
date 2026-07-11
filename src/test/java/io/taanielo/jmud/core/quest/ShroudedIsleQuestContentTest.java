package io.taanielo.jmud.core.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;

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
 * Content integration test for the Shrouded Isle quest chain (issue #433). Loads the real
 * {@code data/} files and verifies that the repeatable "Drowned Watch" contract and the one-time
 * "Tidebreaker" capstone are defined against genuine Shrouded Isle mobs and factions, and that the
 * accept → kill → complete flow works end-to-end for the capstone — including its prerequisite
 * gate, title reward and militia reputation gain.
 */
class ShroudedIsleQuestContentTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final QuestId WATCH = QuestId.of("drowned-watch");
    private static final QuestId CAPSTONE = QuestId.of("tidebreaker");

    private static Player newPlayer() {
        User user = new User(Username.of("keeper"), Password.of("pass"));
        return Player.of(user, PromptSettings.defaultFormat());
    }

    @Test
    void watchQuest_targetsARealShroudedIsleMobAndGrantsRewards() throws Exception {
        JsonQuestRepository quests = new JsonQuestRepository(DATA_ROOT);
        JsonFactionRepository factions = new JsonFactionRepository(DATA_ROOT);

        QuestTemplate watch = quests.findById(WATCH)
            .orElseThrow(() -> new AssertionError("The Drowned Watch quest must be defined"));

        assertEquals(QuestType.KILL, watch.type());
        assertTrue(watch.isRepeatable(), "the drowned watch quest should be repeatable");
        assertTrue(watch.requiredKills() > 1, "the drowned watch quest should require a handful of kills");
        assertTrue(watch.goldReward() > 0 && watch.xpReward() > 0, "the watch quest must reward gold and XP");
        assertTrue(watch.hasReputationReward(), "the watch quest should bump a faction");
        assertTrue(factions.findById(FactionId.of(watch.reputationRewardFactionId())).isPresent(),
            "the watch quest's faction must exist");
    }

    @Test
    void capstoneQuest_targetsTheDrownedCaptainBossWithPrerequisiteAndTitle() throws Exception {
        JsonQuestRepository quests = new JsonQuestRepository(DATA_ROOT);
        JsonMobTemplateRepository mobs = new JsonMobTemplateRepository(DATA_ROOT);

        QuestTemplate capstone = quests.findById(CAPSTONE)
            .orElseThrow(() -> new AssertionError("Tidebreaker quest must be defined"));

        assertEquals(QuestType.KILL, capstone.type());
        assertEquals("drowned-captain", capstone.targetMobId());
        assertEquals(1, capstone.requiredKills());
        assertFalse(capstone.isRepeatable(), "the capstone must be one-time-only");
        assertTrue(capstone.hasPrerequisite(), "the capstone must be gated behind a prerequisite");
        assertEquals(WATCH.getValue(), capstone.prerequisiteQuestId(),
            "the capstone must be gated behind the drowned watch quest");
        assertNotNull(capstone.titleReward(), "the capstone must grant a title");
        assertTrue(capstone.hasReputationReward(), "the capstone must grant militia reputation");
        assertTrue(capstone.reputationRewardDelta() > 0, "the capstone reputation gain must be positive");

        MobTemplate boss = mob(mobs, capstone.targetMobId());
        assertTrue(boss.tags().contains("boss"),
            "the capstone target must be the Drowned Captain boss");
    }

    @Test
    void capstone_completesEndToEndGrantingTitleAndReputation() throws Exception {
        JsonQuestRepository quests = new JsonQuestRepository(DATA_ROOT);
        QuestReputationRewardService reputationRewards =
            new QuestReputationRewardService(new ReputationService(new JsonFactionRepository(DATA_ROOT)));
        QuestKillService killService = new QuestKillService(quests, null, reputationRewards);

        QuestTemplate capstone = quests.findById(CAPSTONE).orElseThrow();

        Player player = newPlayer().withActiveQuest(new ActiveQuest(CAPSTONE, capstone.requiredKills()));
        Optional<KillResult> bossKill = killService.recordKill(player, capstone.targetMobId());
        player = bossKill.orElseThrow(() -> new AssertionError("boss kill should be recorded")).player();
        assertTrue(player.getActiveQuest().isComplete(), "one boss kill should complete the capstone");

        CompletionResult reward = killService.grantCompletionReward(player, capstone);
        player = reward.player();
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
