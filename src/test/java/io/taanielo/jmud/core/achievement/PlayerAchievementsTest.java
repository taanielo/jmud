package io.taanielo.jmud.core.achievement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PlayerAchievementsTest {

    private static final Instant AT = Instant.parse("2026-07-09T10:15:30Z");

    @Test
    void emptyHasNoUnlocks() {
        assertTrue(PlayerAchievements.empty().isEmpty());
        assertEquals(0, PlayerAchievements.empty().size());
    }

    @Test
    void unlockRecordsIdAndTimestamp() {
        PlayerAchievements achievements =
            PlayerAchievements.empty().unlock(AchievementId.of("first_kill"), AT);

        assertTrue(achievements.has(AchievementId.of("first_kill")));
        assertEquals(java.util.Optional.of(AT),
            achievements.unlockedAt(AchievementId.of("first_kill")));
    }

    @Test
    void reUnlockingKeepsOriginalTimestampAndInstance() {
        PlayerAchievements achievements =
            PlayerAchievements.empty().unlock(AchievementId.of("first_kill"), AT);

        PlayerAchievements again =
            achievements.unlock(AchievementId.of("first_kill"), AT.plusSeconds(999));

        assertSame(achievements, again, "Re-unlock must be a no-op returning the same instance");
        assertEquals(java.util.Optional.of(AT),
            again.unlockedAt(AchievementId.of("first_kill")));
    }

    @Test
    void roundTripsThroughStringMap() {
        PlayerAchievements original = PlayerAchievements.empty()
            .unlock(AchievementId.of("first_kill"), AT)
            .unlock(AchievementId.of("kills_100"), AT.plusSeconds(60));

        Map<String, String> raw = original.toStringMap();
        PlayerAchievements restored = PlayerAchievements.fromStringMap(raw);

        assertEquals(2, restored.size());
        assertEquals(java.util.Optional.of(AT), restored.unlockedAt(AchievementId.of("first_kill")));
        assertEquals(java.util.Optional.of(AT.plusSeconds(60)),
            restored.unlockedAt(AchievementId.of("kills_100")));
    }

    @Test
    void fromStringMapTreatsNullAsEmpty() {
        assertTrue(PlayerAchievements.fromStringMap(null).isEmpty());
    }

    @Test
    void unknownAchievementIsNotUnlocked() {
        assertFalse(PlayerAchievements.empty().has(AchievementId.of("nope")));
    }
}
