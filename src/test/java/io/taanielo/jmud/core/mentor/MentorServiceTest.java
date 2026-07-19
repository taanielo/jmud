package io.taanielo.jmud.core.mentor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;

/**
 * Unit tests for {@link MentorService}: the pure eligibility/bonus/graduation rules and the transient
 * proposal registry, all exercised without any networking (AGENTS.md §10).
 */
class MentorServiceTest {

    private static final MentorRankLadder LADDER = new MentorRankLadder(List.of(
        new MentorRank(1, "the Mentor", 5),
        new MentorRank(3, "the Seasoned Mentor", 10),
        new MentorRank(5, "the Master Mentor", 15),
        new MentorRank(10, "the Grandmaster Mentor", 20)));

    private final MentorService service = new MentorService(LADDER);

    private static Player player(String name) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    // ── eligibility rules ───────────────────────────────────────────────

    @Test
    void meetsLevelGapRequiresAtLeastTenLevels() {
        assertTrue(service.meetsLevelGap(20, 5));
        assertTrue(service.meetsLevelGap(15, 5));
        assertFalse(service.meetsLevelGap(14, 5), "gap of 9 is below the floor");
    }

    @Test
    void withinMenteeCeilingIsExclusiveOfTwenty() {
        assertTrue(service.withinMenteeCeiling(19));
        assertFalse(service.withinMenteeCeiling(20));
        assertFalse(service.withinMenteeCeiling(25));
    }

    @Test
    void graduationLevelIsLesserOfMentorMinusTenAndCap() {
        assertEquals(10, service.graduationLevel(20), "min(20-10, 20)");
        assertEquals(20, service.graduationLevel(35), "min(35-10, 20) capped at 20");
    }

    @Test
    void hasGraduatedComparesMenteeLevelToThreshold() {
        assertTrue(service.hasGraduated(10, 20));
        assertTrue(service.hasGraduated(11, 20));
        assertFalse(service.hasGraduated(9, 20));
    }

    @Test
    void menteeBonusXpIsTwentyPercentFlooredAndNeverNegative() {
        assertEquals(10, service.menteeBonusXp(50));
        assertEquals(0, service.menteeBonusXp(0));
        assertEquals(0, service.menteeBonusXp(4), "floor(0.8) is 0");
        assertEquals(1, service.menteeBonusXp(5));
    }

    // ── graduation application ──────────────────────────────────────────

    @Test
    void graduateClearsBothBondsIncrementsCounterAndGrantsFirstTitle() {
        Player mentee = player("Mentee").withMentor("Mentor", 1000L);
        Player mentor = player("Mentor").withMentee("Mentee", 1000L);

        MentorService.GraduationOutcome outcome = service.graduate(mentee, mentor);

        assertFalse(outcome.mentee().hasMentorBond(), "mentee bond cleared");
        assertFalse(outcome.mentor().hasMentorBond(), "mentor bond cleared");
        assertEquals(1, outcome.mentor().menteesGraduated());
        assertEquals(List.of(MentorService.MENTOR_TITLE), outcome.newlyEarnedTitles());
        assertTrue(outcome.mentor().titles().has(MentorService.MENTOR_TITLE));
    }

    @Test
    void graduateGrantsEachRankTitleExactlyOnceAsTheCounterClimbs() {
        Player mentor = player("Mentor").withMentee("A", 1L);
        MentorService.GraduationOutcome first = service.graduate(player("A").withMentor("Mentor", 1L), mentor);
        assertEquals(List.of("the Mentor"), first.newlyEarnedTitles());
        assertEquals(1, first.mentor().menteesGraduated());

        // Second graduation: counter climbs to 2 but no new rank threshold is crossed (next is 3).
        Player mentorAgain = first.mentor().withMentee("B", 2L);
        MentorService.GraduationOutcome second =
            service.graduate(player("B").withMentor("Mentor", 2L), mentorAgain);
        assertEquals(List.of(), second.newlyEarnedTitles(), "no new rank reached at 2 graduations");
        assertEquals(2, second.mentor().menteesGraduated());
        assertTrue(second.mentor().titles().has("the Mentor"));

        // Third graduation crosses the rank-2 threshold, earning the mid-tier title exactly once.
        Player mentorThird = second.mentor().withMentee("C", 3L);
        MentorService.GraduationOutcome third =
            service.graduate(player("C").withMentor("Mentor", 3L), mentorThird);
        assertEquals(List.of("the Seasoned Mentor"), third.newlyEarnedTitles());
        assertEquals(3, third.mentor().menteesGraduated());
        assertTrue(third.mentor().titles().has("the Mentor"), "keeps the first-rung title");
        assertTrue(third.mentor().titles().has("the Seasoned Mentor"));
    }

    @Test
    void mentorBonusXpScalesWithGuildRankAndFloorsAtZeroBelowFirstRank() {
        assertEquals(0, service.mentorBonusXp(100, 0), "no rank yet grants no perk");
        assertEquals(5, service.mentorBonusXp(100, 1), "rank 1 grants +5%");
        assertEquals(10, service.mentorBonusXp(100, 3), "rank 2 grants +10%");
        assertEquals(20, service.mentorBonusXp(100, 25), "top rank caps the perk at +20%");
        assertEquals(0, service.mentorBonusXp(0, 10), "non-positive share yields no bonus");
    }

    // ── proposal registry ───────────────────────────────────────────────

    @Test
    void proposeAndResolveRoundTrip() {
        Username mentor = Username.of("Mentor");
        Username mentee = Username.of("Mentee");

        service.propose(mentor, mentee);
        assertTrue(service.hasPendingProposal(mentee));
        assertEquals(Optional.of(mentor), service.pendingProposer(mentee));

        assertEquals(Optional.of(mentor), service.resolve(mentee));
        assertFalse(service.hasPendingProposal(mentee));
        assertEquals(Optional.empty(), service.resolve(mentee), "already resolved");
    }

    @Test
    void proposalExpiresAfterTimeoutWindow() {
        Username mentee = Username.of("Mentee");
        service.propose(Username.of("Mentor"), mentee);

        for (int i = 0; i < MentorService.DEFAULT_TIMEOUT_TICKS; i++) {
            assertTrue(service.hasPendingProposal(mentee), "still pending at tick " + i);
            service.tick();
        }
        assertFalse(service.hasPendingProposal(mentee), "proposal lapses once the window elapses");
    }

    @Test
    void clearForRemovesProposalsInBothDirections() {
        Username mentor = Username.of("Mentor");
        Username mentee = Username.of("Mentee");
        service.propose(mentor, mentee);

        service.clearFor(mentor);
        assertFalse(service.hasPendingProposal(mentee), "issued proposal cleared when proposer leaves");

        service.propose(mentor, mentee);
        service.clearFor(mentee);
        assertFalse(service.hasPendingProposal(mentee), "received proposal cleared when target leaves");
    }
}
