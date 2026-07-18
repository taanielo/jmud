package io.taanielo.jmud.core.achievement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.achievement.repository.json.JsonAchievementRepository;

/**
 * Content-level guards over the shipped {@code data/achievements/*.json} definitions.
 *
 * <p>These tests load the real data directory (not fixtures) so a broken or unreachable milestone is
 * caught before it ships. The headline invariant — issue #737 — is that no {@code quests_completed}
 * achievement may demand more distinct one-time quests than actually exist under {@code data/quests/}.
 */
class AchievementContentTest {

    private static final Path DATA_ROOT = Path.of("data");

    private static List<Achievement> shippedAchievements() throws AchievementRepositoryException {
        return new JsonAchievementRepository(DATA_ROOT).findAll();
    }

    /**
     * Counts the one-time quest contracts that back the {@code quests_completed} condition: the
     * top-level {@code data/quests/*.json} files only, excluding the {@code daily/} and {@code guild/}
     * subdirectories whose pools do not count toward the completed-quests set.
     */
    private static long oneTimeQuestCount() throws IOException {
        try (var stream = Files.list(DATA_ROOT.resolve("quests"))) {
            return stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json"))
                .count();
        }
    }

    @Test
    void noQuestsCompletedThresholdExceedsOneTimeQuestCount() throws Exception {
        long oneTimeQuests = oneTimeQuestCount();
        List<Achievement> unreachable = shippedAchievements().stream()
            .filter(a -> a.condition() == AchievementCondition.QUESTS_COMPLETED)
            .filter(a -> a.threshold() > oneTimeQuests)
            .toList();

        assertTrue(unreachable.isEmpty(),
            "Every quests_completed achievement must be reachable: only " + oneTimeQuests
                + " one-time quests exist, but these demand more: " + unreachable.stream()
                    .map(a -> a.id().getValue() + "=" + a.threshold()).toList());
    }

    @Test
    void newMilestonesAreShipped() throws Exception {
        List<AchievementId> ids = shippedAchievements().stream().map(Achievement::id).toList();

        assertTrue(ids.contains(AchievementId.of("level_75")), "expected a level 75 milestone");
        assertTrue(ids.contains(AchievementId.of("level_96")), "expected a level 96 milestone");
        assertTrue(ids.contains(AchievementId.of("kills_5000")), "expected a 5000-kill milestone");
        assertTrue(ids.contains(AchievementId.of("quests_30")),
            "expected the reachable quests_30 milestone");
        assertFalse(ids.contains(AchievementId.of("quests_50")),
            "the unreachable quests_50 milestone must be retired");
    }
}
