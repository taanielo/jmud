package io.taanielo.jmud.core.mentor;

import java.util.Objects;

/**
 * One rung on the Mentors' Guild ladder: the recognition and shared perk a mentor earns once their
 * lifetime graduated-mentee count reaches {@code menteesRequired}.
 *
 * <p>Ranks are pure, data-driven value objects loaded from {@code data/mentor/ranks.json}. Each rank
 * carries a unique milestone title granted the moment the threshold is reached, plus a
 * {@code mentorXpBonusPercent} guild perk: while an active mentor of this rank is grouped with their
 * bonded mentee on a shared kill, the mentor earns that flat percentage of their own party XP share
 * as a bonus (additive, never taken from anyone else). Higher ranks supersede lower ones for the
 * perk, so a mentor always enjoys the best perk their standing has unlocked.
 *
 * @param menteesRequired      lifetime graduated mentees needed to reach this rank (at least one)
 * @param title                the unique milestone title granted at this rank
 * @param mentorXpBonusPercent the mentor's own bonus-XP percentage while actively mentoring at this
 *                             rank (non-negative)
 */
public record MentorRank(int menteesRequired, String title, int mentorXpBonusPercent) {

    /**
     * Creates a rank, validating the threshold, title, and perk percentage.
     */
    public MentorRank {
        if (menteesRequired < 1) {
            throw new IllegalArgumentException("Mentor rank menteesRequired must be at least 1, was " + menteesRequired);
        }
        Objects.requireNonNull(title, "Mentor rank title is required");
        if (title.isBlank()) {
            throw new IllegalArgumentException("Mentor rank title must not be blank");
        }
        if (mentorXpBonusPercent < 0) {
            throw new IllegalArgumentException(
                "Mentor rank mentorXpBonusPercent must not be negative, was " + mentorXpBonusPercent);
        }
    }
}
