package io.taanielo.jmud.core.achievement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;

class AchievementServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-09T12:00:00Z");

    private static final Achievement FIRST_KILL =
        new Achievement(AchievementId.of("first_kill"), "First Blood", "Slay your first enemy.",
            AchievementCondition.TOTAL_KILLS, 1);
    private static final Achievement KILLS_100 =
        new Achievement(AchievementId.of("kills_100"), "Centurion", "Slay 100 enemies.",
            AchievementCondition.TOTAL_KILLS, 100);
    private static final Achievement LEVEL_10 =
        new Achievement(AchievementId.of("level_10"), "Seasoned", "Reach level 10.",
            AchievementCondition.LEVEL, 10);

    private static AchievementService serviceOf(Achievement... achievements) throws Exception {
        AchievementRepository repository = new AchievementRepository() {
            @Override
            public List<Achievement> findAll() {
                return List.of(achievements);
            }

            @Override
            public Optional<Achievement> findById(AchievementId id) {
                return List.of(achievements).stream().filter(a -> a.id().equals(id)).findFirst();
            }
        };
        return new AchievementService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Player playerWithKills(long kills) {
        User user = User.of(Username.of("hero"), Password.hash("pw", 1));
        return Player.of(user, "%hp> ").withTotalKills(kills);
    }

    private static Player playerAtLevel(int level) {
        User user = User.of(Username.of("hero"), Password.hash("pw", 1));
        Player base = Player.of(user, "%hp> ");
        return base.withIdentity(base.identity().withLevel(level));
    }

    @Test
    void unlocksAchievementWhenConditionMet() throws Exception {
        AchievementService service = serviceOf(FIRST_KILL, KILLS_100);
        Player player = playerWithKills(1);

        AchievementService.UnlockResult result = service.checkAndUnlock(player);

        assertEquals(List.of(FIRST_KILL), result.newlyUnlocked());
        assertTrue(result.player().achievements().has(AchievementId.of("first_kill")));
        assertEquals(Optional.of(NOW),
            result.player().achievements().unlockedAt(AchievementId.of("first_kill")));
    }

    @Test
    void doesNotUnlockWhenConditionNotMet() throws Exception {
        AchievementService service = serviceOf(KILLS_100);
        Player player = playerWithKills(5);

        AchievementService.UnlockResult result = service.checkAndUnlock(player);

        assertTrue(result.newlyUnlocked().isEmpty());
        assertFalse(result.player().achievements().has(AchievementId.of("kills_100")));
    }

    @Test
    void unlocksMultipleAchievementsInOnePass() throws Exception {
        AchievementService service = serviceOf(FIRST_KILL, KILLS_100);
        Player player = playerWithKills(100);

        AchievementService.UnlockResult result = service.checkAndUnlock(player);

        assertEquals(2, result.newlyUnlocked().size());
        assertTrue(result.player().achievements().has(AchievementId.of("first_kill")));
        assertTrue(result.player().achievements().has(AchievementId.of("kills_100")));
    }

    @Test
    void alreadyUnlockedAchievementIsNotReUnlocked() throws Exception {
        AchievementService service = serviceOf(FIRST_KILL);
        Player player = playerWithKills(1);

        Player afterFirst = service.checkAndUnlock(player).player();
        AchievementService.UnlockResult second = service.checkAndUnlock(afterFirst.withTotalKills(50));

        assertTrue(second.newlyUnlocked().isEmpty(),
            "An already-unlocked achievement must not unlock again");
    }

    @Test
    void unlocksLevelAchievement() throws Exception {
        AchievementService service = serviceOf(LEVEL_10);
        Player player = playerAtLevel(10);

        AchievementService.UnlockResult result = service.checkAndUnlock(player);

        assertEquals(List.of(LEVEL_10), result.newlyUnlocked());
    }

    @Test
    void statusesReportProgressAndUnlockState() throws Exception {
        AchievementService service = serviceOf(FIRST_KILL, KILLS_100);
        Player player = playerWithKills(5);
        Player unlocked = service.checkAndUnlock(player).player();

        List<AchievementService.AchievementStatus> statuses = service.statuses(unlocked);

        AchievementService.AchievementStatus firstKill = statuses.stream()
            .filter(s -> s.achievement().id().equals(AchievementId.of("first_kill")))
            .findFirst().orElseThrow();
        AchievementService.AchievementStatus centurion = statuses.stream()
            .filter(s -> s.achievement().id().equals(AchievementId.of("kills_100")))
            .findFirst().orElseThrow();

        assertTrue(firstKill.unlocked());
        assertEquals(NOW, firstKill.unlockedAt());
        assertFalse(centurion.unlocked());
        assertEquals(5, centurion.progress(), "Progress should reflect current kills");
    }

    @Test
    void progressIsCappedAtThreshold() throws Exception {
        AchievementService service = serviceOf(KILLS_100);
        Player player = playerWithKills(137);

        AchievementService.AchievementStatus status = service.statuses(player).get(0);

        assertEquals(100, status.progress(), "Progress must not exceed the threshold");
    }
}
