package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;

class LevelUpServiceTest {

    private static Player basePlayer(int level, long xp) {
        User user = User.of(Username.of("hero"), Password.hash("pw", 1000));
        PlayerVitals vitals = PlayerVitals.defaults();
        return new Player(user, level, xp, vitals, List.of(), "prompt", false, List.of(), null, null);
    }

    private final LevelUpService service = new LevelUpService();

    // ── xpForNextLevel ────────────────────────────────────────────────────────

    @Test
    void xpForNextLevelScalesWithLevel() {
        assertEquals(100L, LevelUpService.xpForNextLevel(1));
        assertEquals(200L, LevelUpService.xpForNextLevel(2));
        assertEquals(500L, LevelUpService.xpForNextLevel(5));
    }

    // ── awardXp: no level-up ─────────────────────────────────────────────────

    @Test
    void awardXpBelowThresholdDoesNotLevelUp() {
        Player player = basePlayer(1, 0);
        LevelUpService.LevelUpResult result = service.awardXp(player, 50);

        assertFalse(result.leveledUp());
        assertEquals(1, result.player().getLevel());
        assertEquals(50, result.player().getExperience());
    }

    @Test
    void awardingZeroXpIsNoop() {
        Player player = basePlayer(1, 42);
        LevelUpService.LevelUpResult result = service.awardXp(player, 0);

        assertFalse(result.leveledUp());
        assertEquals(1, result.player().getLevel());
        assertEquals(42, result.player().getExperience());
    }

    // ── awardXp: single level-up ─────────────────────────────────────────────

    @Test
    void awardXpExactlyAtThresholdTriggersLevelUp() {
        Player player = basePlayer(1, 0);
        // Level 1 -> 2 requires 100 XP
        LevelUpService.LevelUpResult result = service.awardXp(player, 100);

        assertTrue(result.leveledUp());
        assertEquals(2, result.player().getLevel());
        assertEquals(0, result.player().getExperience());
    }

    @Test
    void excessXpCarriesOverAfterLevelUp() {
        Player player = basePlayer(1, 0);
        // Level 1->2 needs 100; award 150 → 50 XP left at level 2
        LevelUpService.LevelUpResult result = service.awardXp(player, 150);

        assertTrue(result.leveledUp());
        assertEquals(2, result.player().getLevel());
        assertEquals(50, result.player().getExperience());
    }

    @Test
    void levelUpGrantsHpAndManaGain() {
        Player player = basePlayer(1, 0);
        int initialMaxHp = player.getVitals().maxHp();
        int initialMaxMana = player.getVitals().maxMana();

        LevelUpService.LevelUpResult result = service.awardXp(player, 100);

        int expectedMaxHp = initialMaxHp + LevelUpService.HP_GAIN_PER_LEVEL;
        int expectedMaxMana = initialMaxMana + LevelUpService.MANA_GAIN_PER_LEVEL;
        assertEquals(expectedMaxHp, result.player().getVitals().maxHp());
        assertEquals(expectedMaxMana, result.player().getVitals().maxMana());
    }

    @Test
    void levelUpRestoresVitalsToFull() {
        // Player is partially damaged before the level-up
        User user = User.of(Username.of("hero"), Password.hash("pw", 1000));
        PlayerVitals damaged = new PlayerVitals(5, 20, 8, 20, 10, 20);
        Player player = new Player(user, 1, 0, damaged, List.of(), "prompt", false, List.of(), null, null);

        LevelUpService.LevelUpResult result = service.awardXp(player, 100);

        PlayerVitals after = result.player().getVitals();
        assertEquals(after.maxHp(), after.hp(), "HP should be restored to max on level-up");
        assertEquals(after.maxMana(), after.mana(), "Mana should be restored to max on level-up");
    }

    // ── awardXp: multiple level-ups ──────────────────────────────────────────

    @Test
    void awardXpCanTriggerMultipleLevelUps() {
        Player player = basePlayer(1, 0);
        // Level 1->2: 100, Level 2->3: 200 => 300 total to reach level 3
        LevelUpService.LevelUpResult result = service.awardXp(player, 300);

        assertTrue(result.leveledUp());
        assertEquals(3, result.player().getLevel());
        assertEquals(0, result.player().getExperience());
    }

    // ── awardXp: practice points ─────────────────────────────────────────────

    @Test
    void levelUpGrantsOnePracticePoint() {
        Player player = basePlayer(1, 0);
        int initialPracticePoints = player.getPracticePoints();

        LevelUpService.LevelUpResult result = service.awardXp(player, 100);

        assertTrue(result.leveledUp());
        assertEquals(initialPracticePoints + LevelUpService.PRACTICE_POINTS_PER_LEVEL,
            result.player().getPracticePoints(),
            "One practice point should be granted per level-up");
    }

    @Test
    void multipleLevelUpsGrantOnePracticePointEach() {
        Player player = basePlayer(1, 0);
        // Level 1->2: 100, Level 2->3: 200 => 300 total for 2 level-ups
        LevelUpService.LevelUpResult result = service.awardXp(player, 300);

        assertEquals(2, result.player().getLevel() - 1, "Should have gained 2 levels");
        assertEquals(2 * LevelUpService.PRACTICE_POINTS_PER_LEVEL,
            result.player().getPracticePoints(),
            "Two practice points should be granted for two level-ups");
    }

    @Test
    void noLevelUpDoesNotGrantPracticePoints() {
        Player player = basePlayer(1, 0);

        LevelUpService.LevelUpResult result = service.awardXp(player, 50);

        assertFalse(result.leveledUp());
        assertEquals(0, result.player().getPracticePoints(),
            "No practice points should be granted without levelling up");
    }

    // ── awardXp: validation ───────────────────────────────────────────────────

    @Test
    void negativeXpThrows() {
        Player player = basePlayer(1, 0);
        assertThrows(IllegalArgumentException.class, () -> service.awardXp(player, -1));
    }
}
