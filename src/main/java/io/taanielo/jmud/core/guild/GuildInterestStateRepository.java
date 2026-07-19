package io.taanielo.jmud.core.guild;

/**
 * Persistence port for the world-scoped guild-treasury-interest accrual counter (issue #800).
 *
 * <p>{@link GuildInterestTicker} credits passive treasury interest only once every
 * {@link GuildInterestTicker#GAME_DAYS_PER_INTEREST_PERIOD} in-game days, accumulating a running count
 * of elapsed in-game days between payouts. Before this port existed that count lived only in memory, so
 * every server restart reset it to zero and — in a restart-prone environment where the server rarely runs
 * a full period straight — interest was silently never credited. This port persists the running count so
 * accrual survives restarts.
 *
 * <p>Implementations load the persisted count once at startup and persist updates without blocking the
 * tick thread (write-behind, AGENTS.md §5): the counter is bumped at most once per in-game day (roughly
 * once every {@code ticksPerPhase * 2} ticks), never on a per-tick hot path.
 */
public interface GuildInterestStateRepository {

    /**
     * Loads the persisted count of elapsed in-game days since the last interest payout boundary.
     *
     * <p>Called once at startup on the bootstrap thread. A missing, empty, or malformed store yields
     * {@code 0} so a corrupt file can never fabricate accrual, only forfeit at most one period of it.
     *
     * @return the persisted elapsed-in-game-days count, or {@code 0} when none is stored
     */
    long loadGameDaysElapsed();

    /**
     * Persists the current count of elapsed in-game days, replacing any previously stored value.
     *
     * <p>Implementations must perform the actual disk write off the tick thread (write-behind), so this
     * is safe to call from {@link GuildInterestTicker#tick()} on the tick thread.
     *
     * @param gameDaysElapsed the running count of elapsed in-game days to persist (non-negative)
     */
    void saveGameDaysElapsed(long gameDaysElapsed);
}
