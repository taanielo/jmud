package io.taanielo.jmud.core.achievement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** Extracts every {@code "title_reward": "..."} string found anywhere under {@code data/quests/}. */
    private static Set<String> questTitleRewards() throws IOException {
        Pattern pattern = Pattern.compile("\"title_reward\"\\s*:\\s*\"([^\"]+)\"");
        Set<String> titles = new HashSet<>();
        try (var stream = Files.walk(DATA_ROOT.resolve("quests"))) {
            for (Path path : stream.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json")).toList()) {
                Matcher matcher = pattern.matcher(Files.readString(path));
                while (matcher.find()) {
                    titles.add(matcher.group(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        return titles;
    }

    @Test
    void achievementTitleRewardsAreUniqueAndDoNotCollideWithQuests() throws Exception {
        List<String> achievementTitles = shippedAchievements().stream()
            .map(Achievement::titleReward)
            .filter(Objects::nonNull)
            .toList();

        assertFalse(achievementTitles.isEmpty(),
            "expected shipped achievements to grant title rewards");

        // Uniqueness among achievement titles (case-insensitive).
        Set<String> seen = new HashSet<>();
        for (String title : achievementTitles) {
            String key = title.toLowerCase(Locale.ROOT);
            assertTrue(seen.add(key),
                "Achievement title reward is duplicated (case-insensitively): " + title);
        }

        // No collision with any quest title reward (case-insensitive).
        Set<String> questTitles = questTitleRewards();
        for (String title : achievementTitles) {
            assertFalse(questTitles.contains(title.toLowerCase(Locale.ROOT)),
                "Achievement title reward collides with a quest title_reward: " + title);
        }
    }

    @Test
    void mentorTitleDoesNotCollideWithAnyAchievementOrQuestTitle() throws Exception {
        // The MENTOR bond (issue #751) grants a programmatic title on a mentor's first graduated
        // mentee; it must stay unique against every data-authored achievement and quest title_reward.
        String mentorTitle =
            io.taanielo.jmud.core.mentor.MentorService.MENTOR_TITLE.toLowerCase(Locale.ROOT);

        Set<String> achievementTitles = new HashSet<>();
        for (Achievement achievement : shippedAchievements()) {
            if (achievement.titleReward() != null) {
                achievementTitles.add(achievement.titleReward().toLowerCase(Locale.ROOT));
            }
        }
        assertFalse(achievementTitles.contains(mentorTitle),
            "Mentor title collides with an achievement title_reward: " + mentorTitle);
        assertFalse(questTitleRewards().contains(mentorTitle),
            "Mentor title collides with a quest title_reward: " + mentorTitle);
    }

    @Test
    void everyShippedAchievementGrantsATitle() throws Exception {
        List<Achievement> missing = shippedAchievements().stream()
            .filter(a -> a.titleReward() == null)
            .toList();
        assertEquals(List.of(), missing,
            "Every shipped achievement must grant a title reward: " + missing.stream()
                .map(a -> a.id().getValue()).toList());
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
