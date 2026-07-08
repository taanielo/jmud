package io.taanielo.jmud.core.player;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.taanielo.jmud.core.tick.Tickable;

/**
 * Per-session {@link Tickable} that deterministically decays a player's hunger and thirst
 * by a fixed amount each tick (AGENTS.md §5 — runs on the tick thread only).
 *
 * <p>When either value crosses below {@link PlayerSustenance#PENALTY_THRESHOLD} on a given
 * tick, a one-off warning message is emitted so the player knows to eat or drink. Decay is
 * a pure function of ticks elapsed — no wall-clock time is consulted — so behaviour is fully
 * reproducible.
 */
public class SustenanceTicker implements Tickable {

    private final Supplier<Player> playerSupplier;
    /** Consumes the updated player after decay is applied. */
    private final Consumer<Player> playerUpdater;
    /** Delivers a warning line to the player when a threshold is newly crossed. */
    private final Consumer<String> warningSink;
    private final int decayPerTick;

    /**
     * Constructs a sustenance ticker.
     *
     * @param playerSupplier supplies the current in-session player; may return {@code null}
     * @param playerUpdater  consumes a player whose sustenance has decayed
     * @param warningSink    receives a warning message when hunger/thirst newly cross the penalty threshold
     * @param decayPerTick   hunger/thirst points to remove each tick; must be non-negative
     */
    public SustenanceTicker(
        Supplier<Player> playerSupplier,
        Consumer<Player> playerUpdater,
        Consumer<String> warningSink,
        int decayPerTick
    ) {
        this.playerSupplier = Objects.requireNonNull(playerSupplier, "Player supplier is required");
        this.playerUpdater = Objects.requireNonNull(playerUpdater, "Player updater is required");
        this.warningSink = Objects.requireNonNull(warningSink, "Warning sink is required");
        if (decayPerTick < 0) {
            throw new IllegalArgumentException("Decay per tick must be non-negative");
        }
        this.decayPerTick = decayPerTick;
    }

    @Override
    public void tick() {
        Player player = playerSupplier.get();
        if (player == null || player.isDead() || decayPerTick == 0) {
            return;
        }
        PlayerSustenance before = player.getSustenance();
        PlayerSustenance after = before.decay(decayPerTick);
        if (after == before) {
            return;
        }
        playerUpdater.accept(player.withSustenance(after));
        emitThresholdWarnings(before, after);
    }

    private void emitThresholdWarnings(PlayerSustenance before, PlayerSustenance after) {
        int threshold = PlayerSustenance.PENALTY_THRESHOLD;
        if (before.hunger() >= threshold && after.hunger() < threshold) {
            warningSink.accept("You are hungry.");
        }
        if (before.thirst() >= threshold && after.thirst() < threshold) {
            warningSink.accept("You are thirsty.");
        }
    }
}
