package io.taanielo.jmud.core.bounty;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.tick.Tickable;

/**
 * World-level {@link Tickable} that refunds expired bounties to their posters.
 *
 * <p>On each tick it asks {@link BountyService#expireBounties(long, long)} for the bounties that have
 * gone unclaimed past the configured lifespan, then for each one resolves the poster — online or
 * offline — and applies {@link BountyService#applyExpiredRefund(Player, Bounty, long)}, which returns
 * the full stake to the poster's gold and appends a mail note. Persisting that update (to a live
 * session or to disk for an offline poster) is delegated to the injected {@link PosterUpdate}, using
 * the same cross-player update path {@code MAIL} uses. Modelled directly on
 * {@link io.taanielo.jmud.core.auction.AuctionExpiryTicker} (AGENTS.md §5 — all mutation on the tick
 * thread, no new blocking I/O beyond the existing synchronous save convention).
 */
@Slf4j
public class BountyExpiryTicker implements Tickable {

    /** Resolves the current {@link Player} for a poster, live in-session or freshly loaded. */
    @FunctionalInterface
    public interface PosterLookup {
        Optional<Player> find(Username poster);
    }

    /** Persists an updated poster wherever they currently are (live session or on disk). */
    @FunctionalInterface
    public interface PosterUpdate {
        void apply(Player updated);
    }

    private final BountyService bountyService;
    private final LongSupplier currentTick;
    private final LongSupplier expiryTicks;
    private final PosterLookup posterLookup;
    private final PosterUpdate posterUpdate;

    /**
     * Creates the expiry ticker.
     *
     * @param bountyService the service whose bounties are expired and refunded
     * @param currentTick   supplies the current game tick
     * @param expiryTicks   supplies the configured bounty lifespan in ticks
     * @param posterLookup  resolves a poster's current player state (online or offline)
     * @param posterUpdate  persists the refunded poster wherever they are
     */
    public BountyExpiryTicker(
        BountyService bountyService,
        LongSupplier currentTick,
        LongSupplier expiryTicks,
        PosterLookup posterLookup,
        PosterUpdate posterUpdate) {
        this.bountyService = Objects.requireNonNull(bountyService, "bountyService is required");
        this.currentTick = Objects.requireNonNull(currentTick, "currentTick is required");
        this.expiryTicks = Objects.requireNonNull(expiryTicks, "expiryTicks is required");
        this.posterLookup = Objects.requireNonNull(posterLookup, "posterLookup is required");
        this.posterUpdate = Objects.requireNonNull(posterUpdate, "posterUpdate is required");
    }

    /**
     * Expires stale bounties and refunds each poster their full stake. Must only be called on the tick
     * thread.
     */
    @Override
    public void tick() {
        long tick = currentTick.getAsLong();
        List<Bounty> expired = bountyService.expireBounties(tick, expiryTicks.getAsLong());
        for (Bounty bounty : expired) {
            Player poster = posterLookup.find(bounty.backer()).orElse(null);
            if (poster == null) {
                log.warn("Cannot refund expired bounty on {} — poster {} not found",
                    bounty.targetName(), bounty.backer().getValue());
                continue;
            }
            Player updated = bountyService.applyExpiredRefund(poster, bounty, tick);
            posterUpdate.apply(updated);
        }
    }
}
