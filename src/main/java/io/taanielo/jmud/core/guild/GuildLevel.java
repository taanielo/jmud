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
 * <p>Each level carries two things: the lifetime-gold {@code threshold} at which a guild reaches it, and
 * the shared-vault {@code vaultCapacity} it unlocks. A fresh guild is level {@link #ONE} with today's
 * flat 40-slot vault; every level above one raises the cap by ten slots, up to an 80-slot cap at the
 * top level {@link #FIVE}. Rewarding sustained, cooperative deposits with a bigger shared vault gives an
 * established guild a persistent long-term goal without adding any new command.
 */
public enum GuildLevel {

    /** Founding tier: no deposits required, today's flat 40-slot vault. */
    ONE(1, 0, 40),
    /** Reached at 500 lifetime gold; unlocks a 50-slot vault. */
    TWO(2, 500, 50),
    /** Reached at 2,000 lifetime gold; unlocks a 60-slot vault. */
    THREE(3, 2_000, 60),
    /** Reached at 5,000 lifetime gold; unlocks a 70-slot vault. */
    FOUR(4, 5_000, 70),
    /** Top tier, reached at 15,000 lifetime gold; unlocks the 80-slot vault cap. */
    FIVE(5, 15_000, 80);

    private final int rank;
    private final int threshold;
    private final int vaultCapacity;

    GuildLevel(int rank, int threshold, int vaultCapacity) {
        this.rank = rank;
        this.threshold = threshold;
        this.vaultCapacity = vaultCapacity;
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
