package io.taanielo.jmud.core.mentor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure {@link MentorRankLadder} lookup rules driving Mentors' Guild titles and the
 * shared mentor XP perk (issue #752).
 */
class MentorRankLadderTest {

    private final MentorRankLadder ladder = new MentorRankLadder(List.of(
        new MentorRank(5, "the Master Mentor", 15),
        new MentorRank(1, "the Mentor", 5),
        new MentorRank(3, "the Seasoned Mentor", 10)));

    @Test
    void sortsRanksByAscendingThreshold() {
        assertEquals(List.of(1, 3, 5), ladder.ranks().stream().map(MentorRank::menteesRequired).toList());
    }

    @Test
    void rejectsDuplicateThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new MentorRankLadder(List.of(
            new MentorRank(1, "A", 1),
            new MentorRank(1, "B", 2))));
    }

    @Test
    void titlesEarnedAtReturnsEveryReachedRung() {
        assertEquals(List.of(), ladder.titlesEarnedAt(0));
        assertEquals(List.of("the Mentor"), ladder.titlesEarnedAt(1));
        assertEquals(List.of("the Mentor", "the Seasoned Mentor"), ladder.titlesEarnedAt(4));
        assertEquals(List.of("the Mentor", "the Seasoned Mentor", "the Master Mentor"),
            ladder.titlesEarnedAt(9));
    }

    @Test
    void currentAndNextRankReflectStanding() {
        assertEquals(Optional.empty(), ladder.currentRank(0));
        assertEquals("the Mentor", ladder.nextRank(0).orElseThrow().title());

        assertEquals("the Mentor", ladder.currentRank(2).orElseThrow().title());
        assertEquals("the Seasoned Mentor", ladder.nextRank(2).orElseThrow().title());

        assertEquals("the Master Mentor", ladder.currentRank(7).orElseThrow().title());
        assertTrue(ladder.nextRank(7).isEmpty(), "no rung above the top");
    }

    @Test
    void mentorBonusPercentUsesHighestReachedRung() {
        assertEquals(0, ladder.mentorBonusPercent(0));
        assertEquals(5, ladder.mentorBonusPercent(1));
        assertEquals(5, ladder.mentorBonusPercent(2));
        assertEquals(10, ladder.mentorBonusPercent(3));
        assertEquals(15, ladder.mentorBonusPercent(100));
    }
}
