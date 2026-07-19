package io.taanielo.jmud.core.mentor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The ordered Mentors' Guild rank ladder, loaded once from {@code data/mentor/ranks.json}.
 *
 * <p>Ranks are held in ascending {@link MentorRank#menteesRequired()} order. The ladder answers the
 * two questions the mentor system asks each time a bond graduates or a mentor shares a kill: which
 * milestone titles a given lifetime graduated-mentee count has earned, and what shared XP perk that
 * standing currently grants. It is an immutable value object with no dependency on infrastructure, so
 * it is fully unit-testable (AGENTS.md §10).
 */
public final class MentorRankLadder {

    private final List<MentorRank> ranks;

    /**
     * Creates a ladder over the given ranks, sorting them by ascending threshold and rejecting
     * duplicate thresholds.
     *
     * @param ranks the rank rungs; must not be null and must have distinct {@code menteesRequired}
     */
    public MentorRankLadder(List<MentorRank> ranks) {
        Objects.requireNonNull(ranks, "Mentor ranks are required");
        List<MentorRank> sorted = new ArrayList<>(ranks);
        sorted.sort(Comparator.comparingInt(MentorRank::menteesRequired));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).menteesRequired() == sorted.get(i - 1).menteesRequired()) {
                throw new IllegalArgumentException(
                    "Duplicate mentor rank threshold " + sorted.get(i).menteesRequired());
            }
        }
        this.ranks = List.copyOf(sorted);
    }

    /**
     * Returns all rungs in ascending threshold order.
     *
     * @return the immutable, ordered rank list
     */
    public List<MentorRank> ranks() {
        return ranks;
    }

    /**
     * Returns the titles of every rank whose threshold has been reached by {@code graduatedCount}, in
     * ascending threshold order. Used at graduation to grant any newly (or retroactively) earned
     * milestone titles.
     *
     * @param graduatedCount the mentor's lifetime graduated-mentee count
     * @return the earned rank titles (possibly empty)
     */
    public List<String> titlesEarnedAt(int graduatedCount) {
        List<String> titles = new ArrayList<>();
        for (MentorRank rank : ranks) {
            if (rank.menteesRequired() <= graduatedCount) {
                titles.add(rank.title());
            }
        }
        return List.copyOf(titles);
    }

    /**
     * Returns the highest rank a mentor with {@code graduatedCount} graduated mentees has attained, or
     * empty when they have not yet reached the first rung.
     *
     * @param graduatedCount the mentor's lifetime graduated-mentee count
     * @return the current rank, or empty when below the first threshold
     */
    public Optional<MentorRank> currentRank(int graduatedCount) {
        MentorRank current = null;
        for (MentorRank rank : ranks) {
            if (rank.menteesRequired() <= graduatedCount) {
                current = rank;
            }
        }
        return Optional.ofNullable(current);
    }

    /**
     * Returns the next rank above a mentor with {@code graduatedCount} graduated mentees, or empty
     * when they already hold the top rung.
     *
     * @param graduatedCount the mentor's lifetime graduated-mentee count
     * @return the next rank to strive for, or empty at the top of the ladder
     */
    public Optional<MentorRank> nextRank(int graduatedCount) {
        for (MentorRank rank : ranks) {
            if (rank.menteesRequired() > graduatedCount) {
                return Optional.of(rank);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the shared Mentors' Guild XP perk percentage currently unlocked by {@code graduatedCount}
     * graduated mentees: the {@link MentorRank#mentorXpBonusPercent()} of the highest rank reached, or
     * zero when no rank has been attained yet.
     *
     * @param graduatedCount the mentor's lifetime graduated-mentee count
     * @return the mentor's active bonus-XP percentage while mentoring
     */
    public int mentorBonusPercent(int graduatedCount) {
        return currentRank(graduatedCount).map(MentorRank::mentorXpBonusPercent).orElse(0);
    }
}
