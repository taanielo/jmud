package io.taanielo.jmud.core.mentor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.tick.Tickable;

/**
 * Owns the mentor-bond system (see the MENTOR command): the transient proposal registry plus the
 * pure domain rules for eligibility, the mentee XP bonus, and graduation.
 *
 * <p>Only the <em>proposal</em> half of a bond is transient and owned here; the persistent bond
 * itself is stored on {@link Player} so it survives logout and server restart. The proposal registry
 * mirrors {@link io.taanielo.jmud.core.social.MarriageService}: proposals are keyed by the target
 * (proposed-to mentee) {@link Username}, each carries a countdown until it silently expires, and
 * mutating operations are {@code synchronized} so bookkeeping stays consistent when a reader thread
 * clears state on disconnect concurrently with the tick thread. All game-state mutation itself still
 * happens on the tick thread (AGENTS.md §5).
 *
 * <p>As a {@link Tickable}, this service decrements each pending proposal's window once per tick and
 * silently discards proposals that time out.
 */
public class MentorService implements Tickable {

    /** Default acceptance window for a mentor proposal, in ticks (~60 seconds at 1s/tick). */
    public static final int DEFAULT_TIMEOUT_TICKS = 60;

    /** Minimum number of levels the mentor must be above the mentee to form (or keep) a bond. */
    public static final int MIN_LEVEL_GAP = 10;

    /** Exclusive upper bound on the mentee's level when a bond is proposed (targets genuine newcomers). */
    public static final int MENTEE_LEVEL_CEILING = 20;

    /** Hard cap on the mentee level at which graduation triggers, regardless of the mentor's level. */
    public static final int GRADUATION_LEVEL_CAP = 20;

    /** Flat percentage bonus added to a mentee's own party XP share on a shared kill. */
    public static final int MENTEE_XP_BONUS_PERCENT = 20;

    /** Unique title granted to a mentor on their first graduated mentee (the ladder's first rung). */
    public static final String MENTOR_TITLE = "the Mentor";

    /** target (mentee) → the pending proposal awaiting their response. */
    private final Map<Username, MentorProposal> proposals = new ConcurrentHashMap<>();

    /** The Mentors' Guild rank ladder driving milestone titles and the shared mentor XP perk. */
    private final MentorRankLadder ranks;

    /**
     * Creates a mentor service over the given Mentors' Guild rank ladder.
     *
     * @param ranks the rank ladder loaded from game data; must not be null
     */
    public MentorService(MentorRankLadder ranks) {
        this.ranks = Objects.requireNonNull(ranks, "Mentor rank ladder is required");
    }

    /**
     * Records a pending mentor proposal from {@code proposer} to {@code target}, starting the default
     * acceptance window. Any existing proposal held against {@code target} is replaced.
     *
     * @param proposer the higher-level player offering to mentor
     * @param target   the proposed-to newcomer
     */
    public synchronized void propose(Username proposer, Username target) {
        Objects.requireNonNull(proposer, "Proposer is required");
        Objects.requireNonNull(target, "Target is required");
        proposals.put(target, new MentorProposal(proposer, DEFAULT_TIMEOUT_TICKS));
    }

    /**
     * Returns the proposer of a pending proposal awaiting {@code target}'s response, if any.
     *
     * @param target the proposed-to newcomer
     * @return the proposer's username, or empty when no pending proposal exists
     */
    public synchronized Optional<Username> pendingProposer(Username target) {
        Objects.requireNonNull(target, "Target is required");
        MentorProposal proposal = proposals.get(target);
        return proposal == null ? Optional.empty() : Optional.of(proposal.proposer());
    }

    /**
     * Returns whether the given player currently has an outstanding proposal awaiting their response.
     *
     * @param target the proposed-to newcomer
     * @return {@code true} when a pending proposal is held against {@code target}
     */
    public synchronized boolean hasPendingProposal(Username target) {
        Objects.requireNonNull(target, "Target is required");
        return proposals.containsKey(target);
    }

    /**
     * Consumes and returns the pending proposal awaiting {@code target}'s response, removing it from
     * the registry. Used by both accept and decline, since either resolves the proposal.
     *
     * @param target the proposed-to newcomer
     * @return the proposer's username, or empty when there was nothing pending
     */
    public synchronized Optional<Username> resolve(Username target) {
        Objects.requireNonNull(target, "Target is required");
        MentorProposal proposal = proposals.remove(target);
        return proposal == null ? Optional.empty() : Optional.of(proposal.proposer());
    }

    /**
     * Clears every proposal touching the given player: one awaiting their response and any they issued
     * to someone else. Called when a player disconnects, so a stale proposal can never block the other
     * party.
     *
     * @param player the player whose proposal involvement should be cleared
     */
    public synchronized void clearFor(Username player) {
        Objects.requireNonNull(player, "Player is required");
        proposals.remove(player);
        proposals.values().removeIf(proposal -> proposal.proposer().equals(player));
    }

    @Override
    public synchronized void tick() {
        proposals.replaceAll((target, proposal) -> proposal.tickDown());
        proposals.values().removeIf(MentorProposal::expired);
    }

    // ── Domain rules (pure) ─────────────────────────────────────────────

    /**
     * Returns whether the mentor is far enough above the mentee's level to form a bond.
     *
     * @param mentorLevel the prospective mentor's level
     * @param menteeLevel the prospective mentee's level
     * @return {@code true} when the level gap meets {@link #MIN_LEVEL_GAP}
     */
    public boolean meetsLevelGap(int mentorLevel, int menteeLevel) {
        return mentorLevel - menteeLevel >= MIN_LEVEL_GAP;
    }

    /**
     * Returns whether the prospective mentee is a genuine newcomer eligible to be mentored, i.e. their
     * level is strictly below {@link #MENTEE_LEVEL_CEILING}.
     *
     * @param menteeLevel the prospective mentee's level
     * @return {@code true} when the mentee is below the ceiling
     */
    public boolean withinMenteeCeiling(int menteeLevel) {
        return menteeLevel < MENTEE_LEVEL_CEILING;
    }

    /**
     * Returns the mentee level at which a bond auto-graduates: the lesser of the mentor's level minus
     * {@link #MIN_LEVEL_GAP} and the {@link #GRADUATION_LEVEL_CAP}.
     *
     * @param mentorLevel the mentor's current level
     * @return the mentee level that triggers graduation
     */
    public int graduationLevel(int mentorLevel) {
        return Math.min(mentorLevel - MIN_LEVEL_GAP, GRADUATION_LEVEL_CAP);
    }

    /**
     * Returns whether a mentee at {@code menteeLevel} has reached the graduation threshold for a
     * mentor at {@code mentorLevel}.
     *
     * @param menteeLevel the mentee's current level
     * @param mentorLevel the mentor's current level
     * @return {@code true} when the mentee should graduate
     */
    public boolean hasGraduated(int menteeLevel, int mentorLevel) {
        return menteeLevel >= graduationLevel(mentorLevel);
    }

    /**
     * Returns the flat bonus XP a mentee earns on top of a party XP share of {@code baseShare}, or 0
     * when the share is non-positive. Additive: this is never taken from any other party member.
     *
     * @param baseShare the mentee's own computed party XP share for a kill
     * @return the bonus XP to add to that share
     */
    public int menteeBonusXp(int baseShare) {
        if (baseShare <= 0) {
            return 0;
        }
        return (int) Math.floor(baseShare * (MENTEE_XP_BONUS_PERCENT / 100.0));
    }

    /**
     * Returns the flat Mentors' Guild bonus XP a mentor earns on top of their own party XP share of
     * {@code baseShare} while grouped with their bonded mentee, or 0 when the share is non-positive or
     * the mentor's rank is unlocked no perk yet. The percentage scales with the mentor's lifetime
     * graduated-mentee count (their guild standing). Additive: never taken from any other member.
     *
     * @param baseShare      the mentor's own computed party XP share for a kill
     * @param graduatedCount the mentor's lifetime graduated-mentee count
     * @return the bonus XP to add to that share
     */
    public int mentorBonusXp(int baseShare, int graduatedCount) {
        if (baseShare <= 0) {
            return 0;
        }
        int percent = ranks.mentorBonusPercent(graduatedCount);
        return (int) Math.floor(baseShare * (percent / 100.0));
    }

    /**
     * Returns the Mentors' Guild rank ladder driving milestone titles and the shared mentor XP perk.
     *
     * @return the immutable rank ladder
     */
    public MentorRankLadder ranks() {
        return ranks;
    }

    /**
     * Applies graduation to a bonded {@code mentee}/{@code mentor} pair: both records have their bond
     * cleared, the mentor's lifetime graduation counter is incremented, and any Mentors' Guild rank
     * milestone titles the new count has reached (and the mentor does not already hold) are granted.
     * Idempotent with respect to titles: a mentor who graduates several mentees earns each rank title
     * exactly once, and climbing to a higher rung grants only the newly reached title(s).
     *
     * @param mentee the graduating mentee (must currently be bonded to {@code mentor})
     * @param mentor the mentor whose mentee is graduating
     * @return the updated players and the mentor titles newly earned by this graduation
     */
    public GraduationOutcome graduate(Player mentee, Player mentor) {
        Objects.requireNonNull(mentee, "Mentee is required");
        Objects.requireNonNull(mentor, "Mentor is required");
        Player updatedMentee = mentee.withoutMentorBond();
        Player updatedMentor = mentor
            .withMenteesGraduated(mentor.menteesGraduated() + 1)
            .withoutMentorBond();
        List<String> newlyEarnedTitles = new ArrayList<>();
        for (String title : ranks.titlesEarnedAt(updatedMentor.menteesGraduated())) {
            if (!updatedMentor.titles().has(title)) {
                updatedMentor = updatedMentor.grantTitle(title);
                newlyEarnedTitles.add(title);
            }
        }
        return new GraduationOutcome(updatedMentee, updatedMentor, List.copyOf(newlyEarnedTitles));
    }

    /**
     * The result of {@link #graduate(Player, Player)}: the updated (bond-cleared) mentee and mentor,
     * and any Mentors' Guild rank titles this graduation newly earned the mentor (in ascending rank
     * order, empty when none).
     *
     * @param mentee            the mentee with their bond cleared
     * @param mentor            the mentor with their bond cleared and counter incremented
     * @param newlyEarnedTitles the mentor rank titles newly granted by this graduation
     */
    public record GraduationOutcome(Player mentee, Player mentor, List<String> newlyEarnedTitles) {

        /** Compact constructor defensively copying the title list. */
        public GraduationOutcome {
            newlyEarnedTitles = List.copyOf(newlyEarnedTitles);
        }
    }
}
