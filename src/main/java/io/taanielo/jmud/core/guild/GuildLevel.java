package io.taanielo.jmud.core.guild;

import java.util.Optional;

/**
 * The progression tier of a {@link Guild}, derived purely from its lifetime deposited gold.
 *
 * <p>A guild's level is a pure function of how much gold has ever been paid into its treasury via
 * {@code GUILD DEPOSIT} over its whole lifetime (withdrawals never reduce it). Levels are never stored
 * on the guild; they are always recomputed from {@link Guild#lifetimeDepositedGold()} on read, so there
 * is no redundant state to keep in sync.
 *
 * <p>Each level carries three things: the lifetime-gold {@code threshold} at which a guild reaches it,
 * the shared-vault {@code vaultCapacity} it unlocks, and the daily treasury {@code interestRatePercent}
 * it earns. A fresh guild is level {@link #ONE} with today's flat 40-slot vault; every level above one
 * raises the cap by ten slots, up to an 80-slot cap at the top level {@link #FIVE}. Rewarding sustained,
 * cooperative deposits with a bigger shared vault gives an established guild a persistent long-term goal
 * without adding any new command.
 *
 * <p>The {@code interestRatePercent} is a modest, level-scaled percentage of the guild's current treasury
 * balance credited once per in-game day (see {@code GuildInterestTicker}). It rises from 1% at level one
 * to 5% at level five, making a guild's level matter for the treasury's growth, not just its vault
 * ceiling. Interest is credited to {@code treasuryGold} only and never counts toward
 * {@link Guild#lifetimeDepositedGold()}, so it can never drive leveling.
 */
public enum GuildLevel {

    /** Founding tier: no deposits required, today's flat 40-slot vault, 1% daily treasury interest. */
    ONE(1, 0, 40, 1),
    /** Reached at 500 lifetime gold; unlocks a 50-slot vault and 2% daily treasury interest. */
    TWO(2, 500, 50, 2),
    /** Reached at 2,000 lifetime gold; unlocks a 60-slot vault and 3% daily treasury interest. */
    THREE(3, 2_000, 60, 3),
    /** Reached at 5,000 lifetime gold; unlocks a 70-slot vault and 4% daily treasury interest. */
    FOUR(4, 5_000, 70, 4),
    /** Top tier, reached at 15,000 lifetime gold; unlocks the 80-slot vault cap and 5% interest. */
    FIVE(5, 15_000, 80, 5);

    private final int rank;
    private final int threshold;
    private final int vaultCapacity;
    private final int interestRatePercent;

    GuildLevel(int rank, int threshold, int vaultCapacity, int interestRatePercent) {
        this.rank = rank;
        this.threshold = threshold;
        this.vaultCapacity = vaultCapacity;
        this.interestRatePercent = interestRatePercent;
    }

    /**
     * Returns the level a guild with the given lifetime deposited gold has reached.
     *
     * @param lifetimeDepositedGold the running total of all gold ever deposited into the treasury
     * @return the highest level whose threshold the total meets or exceeds (never below {@link #ONE})
     */
    public static GuildLevel forLifetimeGold(int lifetimeDepositedGold) {
        GuildLevel result = ONE;
        for (GuildLevel level : values()) {
            if (lifetimeDepositedGold >= level.threshold) {
                result = level;
            }
        }
        return result;
    }

    /** Returns this level's numeric rank, from 1 ({@link #ONE}) to 5 ({@link #FIVE}). */
    public int rank() {
        return rank;
    }

    /** Returns the lifetime deposited gold at which a guild reaches this level. */
    public int threshold() {
        return threshold;
    }

    /** Returns the shared item-vault capacity, in slots, unlocked at this level. */
    public int vaultCapacity() {
        return vaultCapacity;
    }

    /**
     * Returns the whole-number percentage of a guild's current treasury balance credited as passive
     * interest once per in-game day at this level, rising from 1% at {@link #ONE} to 5% at {@link #FIVE}.
     *
     * @return the daily treasury interest rate, in percent
     */
    public int interestRatePercent() {
        return interestRatePercent;
    }

    /** Returns {@code true} when this is the top level and no further progression is possible. */
    public boolean isMax() {
        return this == FIVE;
    }

    /**
     * Returns the next level up, or empty when this is already the top level.
     *
     * @return the next {@link GuildLevel}, or empty at {@link #FIVE}
     */
    public Optional<GuildLevel> next() {
        return switch (this) {
            case ONE -> Optional.of(TWO);
            case TWO -> Optional.of(THREE);
            case THREE -> Optional.of(FOUR);
            case FOUR -> Optional.of(FIVE);
            case FIVE -> Optional.empty();
        };
    }
}
