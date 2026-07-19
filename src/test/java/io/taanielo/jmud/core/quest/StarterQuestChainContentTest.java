package io.taanielo.jmud.core.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.prompt.PromptSettings;
import io.taanielo.jmud.core.quest.repository.json.JsonQuestRepository;

/**
 * Content integration test for the level-1 starter quest chain (issue #518). Loads the real
 * {@code data/} files and verifies the {@code rat-catcher → goblin-thrasher → kobold-hunter} ramp:
 * each contract carries an ascending {@code recommended_level} difficulty hint, the chain is gated
 * with prerequisites so a brand-new character is funnelled to the Rat Catcher first, and the
 * easiest-first ordering used by {@code QUEST LIST} surfaces the starter quest at the top.
 */
class StarterQuestChainContentTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final QuestId RAT_CATCHER = QuestId.of("rat-catcher");
    private static final QuestId GOBLIN_THRASHER = QuestId.of("goblin-thrasher");
    private static final QuestId KOBOLD_HUNTER = QuestId.of("kobold-hunter");
    private static final QuestId SPIDER_SLAYER = QuestId.of("spider-slayer");

    private static Player newPlayer() {
        User user = new User(Username.of("newbie"), Password.of("pass"));
        return Player.of(user, PromptSettings.defaultFormat());
    }

    @Test
    void ratCatcherIsTheLevelOneStarterQuest() throws Exception {
        JsonQuestRepository quests = new JsonQuestRepository(DATA_ROOT);
        QuestTemplate rat = quests.findById(RAT_CATCHER)
            .orElseThrow(() -> new AssertionError("rat-catcher must be defined"));

        assertEquals(QuestType.KILL, rat.type());
        assertTrue(rat.hasRecommendedLevel(), "the starter quest must carry a level recommendation");
        assertEquals(1, rat.recommendedLevel(), "the starter quest should be recommended for level 1");
        assertFalse(rat.hasPrerequisite(), "the first quest in the chain must have no prerequisite");
        assertFalse(rat.isRepeatable(), "the chain starter is a one-time contract so it can gate the chain");
    }

    @Test
    void chainRampsAscendingWithPrerequisites() throws Exception {
        JsonQuestRepository quests = new JsonQuestRepository(DATA_ROOT);
        QuestTemplate rat = quests.findById(RAT_CATCHER).orElseThrow();
        QuestTemplate goblin = quests.findById(GOBLIN_THRASHER).orElseThrow();
        QuestTemplate kobold = quests.findById(KOBOLD_HUNTER).orElseThrow();
        QuestTemplate spider = quests.findById(SPIDER_SLAYER).orElseThrow();

        // Recommended levels form a strictly ascending ramp.
        assertTrue(rat.recommendedLevel() < goblin.recommendedLevel(),
            "goblin-thrasher must be recommended above rat-catcher");
        assertTrue(goblin.recommendedLevel() < kobold.recommendedLevel(),
            "kobold-hunter must be recommended above goblin-thrasher");
        assertTrue(spider.hasRecommendedLevel(), "spider-slayer must carry a level recommendation");
        assertTrue(kobold.recommendedLevel() < spider.recommendedLevel(),
            "spider-slayer must be recommended above kobold-hunter");

        // Each later contract is gated behind the previous one, forming the chain.
        assertTrue(goblin.hasPrerequisite());
        assertEquals(RAT_CATCHER.getValue(), goblin.prerequisiteQuestId());
        assertTrue(kobold.hasPrerequisite());
        assertEquals(GOBLIN_THRASHER.getValue(), kobold.prerequisiteQuestId());
        assertTrue(spider.hasPrerequisite());
        assertEquals(KOBOLD_HUNTER.getValue(), spider.prerequisiteQuestId());
    }

    @Test
    void freshPlayerSeesOnlyRatCatcherFromTheChainAndItSortsFirst() throws Exception {
        JsonQuestRepository quests = new JsonQuestRepository(DATA_ROOT);
        Player fresh = newPlayer();

        // Mirror QUEST LIST: hide unmet-prerequisite quests, sort easiest-first.
        List<QuestTemplate> visible = quests.findAll().stream()
            .filter(q -> !q.isNpcDeliveryQuest())
            .filter(q -> !q.hasPrerequisite()
                || fresh.completedQuests().hasCompleted(QuestId.of(q.prerequisiteQuestId())))
            .sorted(Comparator
                .comparingInt((QuestTemplate q) ->
                    q.hasRecommendedLevel() ? q.recommendedLevel() : Integer.MAX_VALUE)
                .thenComparing(q -> q.id().getValue()))
            .toList();

        assertFalse(visible.stream().anyMatch(q -> q.id().equals(GOBLIN_THRASHER)),
            "goblin-thrasher must be hidden until rat-catcher is completed");
        assertFalse(visible.stream().anyMatch(q -> q.id().equals(KOBOLD_HUNTER)),
            "kobold-hunter must be hidden until goblin-thrasher is completed");
        assertFalse(visible.stream().anyMatch(q -> q.id().equals(SPIDER_SLAYER)),
            "spider-slayer must be hidden until kobold-hunter is completed");
        assertEquals(RAT_CATCHER, visible.get(0).id(),
            "the level-1 rat-catcher must sort first in the easiest-first listing");

        // Once rat-catcher is done, goblin-thrasher becomes visible.
        Player veteran = fresh.withCompletedQuest(RAT_CATCHER);
        boolean goblinVisible = quests.findById(GOBLIN_THRASHER).orElseThrow().hasPrerequisite()
            && veteran.completedQuests().hasCompleted(RAT_CATCHER);
        assertTrue(goblinVisible, "goblin-thrasher unlocks once rat-catcher is completed");
    }

    @Test
    void spiderSlayerUnlocksOnlyAfterKoboldHunterAndSortsLast() throws Exception {
        JsonQuestRepository quests = new JsonQuestRepository(DATA_ROOT);

        // A character who has walked the whole ramp has spider-slayer as the last visible rung.
        Player ramped = newPlayer()
            .withCompletedQuest(RAT_CATCHER)
            .withCompletedQuest(GOBLIN_THRASHER)
            .withCompletedQuest(KOBOLD_HUNTER);

        List<QuestTemplate> visible = quests.findAll().stream()
            .filter(q -> !q.isNpcDeliveryQuest())
            .filter(q -> !q.hasPrerequisite()
                || ramped.completedQuests().hasCompleted(QuestId.of(q.prerequisiteQuestId())))
            .sorted(Comparator
                .comparingInt((QuestTemplate q) ->
                    q.hasRecommendedLevel() ? q.recommendedLevel() : Integer.MAX_VALUE)
                .thenComparing(q -> q.id().getValue()))
            .toList();

        assertTrue(visible.stream().anyMatch(q -> q.id().equals(SPIDER_SLAYER)),
            "spider-slayer unlocks once kobold-hunter is completed");

        // Spider-slayer sorts after the three earlier chain rungs in the easiest-first listing.
        int ratIdx = indexOf(visible, RAT_CATCHER);
        int goblinIdx = indexOf(visible, GOBLIN_THRASHER);
        int koboldIdx = indexOf(visible, KOBOLD_HUNTER);
        int spiderIdx = indexOf(visible, SPIDER_SLAYER);
        assertTrue(ratIdx < goblinIdx && goblinIdx < koboldIdx && koboldIdx < spiderIdx,
            "the four starter rungs must list in ramp order rat → goblin → kobold → spider");
    }

    private static int indexOf(List<QuestTemplate> quests, QuestId id) {
        for (int i = 0; i < quests.size(); i++) {
            if (quests.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new AssertionError(id + " must be present in the visible listing");
    }
}
