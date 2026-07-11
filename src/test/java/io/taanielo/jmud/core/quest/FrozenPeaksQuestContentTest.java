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
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.json.JsonItemRepository;

/**
 * Content integration test for the Frozen Peaks quest chain (issue #415). Loads the real
 * {@code data/} files and verifies that the "Frostbound Cull" and "Vharixis's End" quests are
 * defined against genuine Frozen Peaks mobs, factions and items, and that the accept → kill →
 * complete flow works end-to-end for both — including the one-time capstone's prerequisite gate,
 * title reward and item reward.
 */
class FrozenPeaksQuestContentTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final QuestId CULL = QuestId.of("frostbound-cull");
    private static final QuestId CAPSTONE = QuestId.of("vharixis-end");

    private static Player newPlayer() {
        User user = new User(Username.of("climber"), Password.of("pass"));
        return Player.of(user, PromptSettings.defaultFormat());
    }

    @Test
    void cullQuest_targetsARealFrozenPeaksMobAndGrantsRewards() throws Exception {
        JsonQuestRepository quests = new JsonQuestRepository(DATA_ROOT);
        JsonMobTemplateRepository mobs = new JsonMobTemplateRepository(DATA_ROOT);
        JsonFactionRepository factions = new JsonFactionRepository(DATA_ROOT);

        QuestTemplate cull = quests.findById(CULL)
            .orElseThrow(() -> new AssertionError("Frostbound Cull quest must be defined"));

        assertEquals(QuestType.KILL, cull.type());
        assertTrue(cull.isRepeatable(), "the cull quest should be repeatable");
        assertTrue(cull.requiredKills() > 1, "the cull quest should require a handful of kills");
        assertTrue(cull.goldReward() > 0 && cull.xpReward() > 0, "the cull quest must reward gold and XP");

        MobTemplate target = mob(mobs, cull.targetMobId());
        assertFalse(target.worldBoss(), "the cull quest must target a non-boss mob");
        assertTrue(target.tags().contains("frozen-peaks"),
            "the cull target must be a Frozen Peaks mob, was " + cull.targetMobId());

        assertTrue(cull.hasReputationReward(), "the cull quest should bump a faction");
        assertTrue(factions.findById(FactionId.of(cull.reputationRewardFactionId())).isPresent(),
            "the cull quest's faction must exist");
        assertTrue(cull.reputationRewardDelta() > 0, "the cull reputation bump must be positive");
    }

    @Test
    void capstoneQuest_targetsTheWorldBossWithPrerequisiteTitleAndItemReward() throws Exception {
        JsonQuestRepository quests = new JsonQuestRepository(DATA_ROOT);
        JsonMobTemplateRepository mobs = new JsonMobTemplateRepository(DATA_ROOT);
        JsonItemRepository items = new JsonItemRepository(DATA_ROOT);

        QuestTemplate capstone = quests.findById(CAPSTONE)
            .orElseThrow(() -> new AssertionError("Vharixis's End quest must be defined"));

        assertEquals(QuestType.KILL, capstone.type());
        assertEquals("frost-wyrm", capstone.targetMobId());
        assertEquals(1, capstone.requiredKills());
        assertFalse(capstone.isRepeatable(), "the capstone must be one-time-only");
        assertTrue(capstone.hasPrerequisite(), "the capstone must be gated behind a prerequisite");
        assertEquals(CULL.getValue(), capstone.prerequisiteQuestId(),
            "the capstone must be gated behind the cull quest");
        assertNotNull(capstone.titleReward(), "the capstone must grant a title");

        MobTemplate boss = mob(mobs, capstone.targetMobId());
        assertTrue(boss.worldBoss(), "the capstone target must be the frost-wyrm world boss");

        assertTrue(capstone.hasItemReward(), "the capstone should grant an item reward");
        assertTrue(items.findById(ItemId.of(capstone.itemReward())).isPresent(),
            "the capstone's item reward must exist under data/items/");
    }

    @Test
    void bothQuests_completeEndToEnd() throws Exception {
        JsonQuestRepository quests = new JsonQuestRepository(DATA_ROOT);
        QuestReputationRewardService reputationRewards =
            new QuestReputationRewardService(new ReputationService(new JsonFactionRepository(DATA_ROOT)));
        QuestKillService killService = new QuestKillService(quests, null, reputationRewards);

        QuestTemplate cull = quests.findById(CULL).orElseThrow();
        QuestTemplate capstone = quests.findById(CAPSTONE).orElseThrow();

        // Accept and complete the repeatable cull quest by killing the required lesser mobs.
        Player player = newPlayer().withActiveQuest(new ActiveQuest(CULL, cull.requiredKills()));
        for (int i = 0; i < cull.requiredKills(); i++) {
            KillResult result = killService.recordKill(player, cull.targetMobId())
                .orElseThrow(() -> new AssertionError("cull kill should be recorded"));
            player = result.player();
        }
        assertTrue(player.getActiveQuest().isComplete(), "the cull quest should be complete after the kills");
        CompletionResult cullReward = killService.grantCompletionReward(player, cull);
        player = cullReward.player();
        assertTrue(player.reputation().standing(FactionId.of(cull.reputationRewardFactionId())) > 0,
            "completing the cull quest should raise faction standing");

        // Now the capstone: kill the world boss once and claim the title + item reward.
        player = player.withActiveQuest(new ActiveQuest(CAPSTONE, capstone.requiredKills()));
        Optional<KillResult> bossKill = killService.recordKill(player, capstone.targetMobId());
        player = bossKill.orElseThrow(() -> new AssertionError("boss kill should be recorded")).player();
        assertTrue(player.getActiveQuest().isComplete(), "one boss kill should complete the capstone");

        CompletionResult capstoneReward = killService.grantCompletionReward(player, capstone);
        player = capstoneReward.player();
        assertTrue(player.titles().has(capstone.titleReward()),
            "completing the capstone should grant its title");
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
