package io.taanielo.jmud.core.player;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.taanielo.jmud.core.tick.Tickable;

/**
 * Per-session {@link Tickable} that regenerates HP, mana, and move while a
 * player is resting.
 *
 * <p>Each tick the player's vitals are increased by the configured regen amounts.
 * When all three vitals reach their maximum the rest ends automatically and the
 * player is notified via the provided callbacks. If the resting flag is cleared
 * elsewhere (e.g. by a mob hit or an action command) this ticker becomes a no-op.
 */
public class RestingTicker implements Tickable {

    private final Supplier<Player> playerSupplier;
    /** Called with the updated player when a tick regen is applied. */
    private final Consumer<Player> playerUpdater;
    /**
     * Called when resting ends automatically (fully rested).
     * First argument is the message to send; second argument is the updated player.
     */
    private final BiConsumer<String, Player> onFullyRested;

    private final int regenHp;
    private final int regenMana;
    private final int regenMove;

    /**
     * Constructs a resting ticker with the given configuration.
     *
     * @param playerSupplier supplies the current in-session player
     * @param playerUpdater  consumes a player whose vitals have been regenerated
     * @param onFullyRested  called with a message and the updated player when all vitals reach max
     * @param regenHp        HP to restore per tick
     * @param regenMana      mana to restore per tick
     * @param regenMove      move to restore per tick
     */
    public RestingTicker(
        Supplier<Player> playerSupplier,
        Consumer<Player> playerUpdater,
        BiConsumer<String, Player> onFullyRested,
        int regenHp,
        int regenMana,
        int regenMove
    ) {
        this.playerSupplier = Objects.requireNonNull(playerSupplier, "Player supplier is required");
        this.playerUpdater = Objects.requireNonNull(playerUpdater, "Player updater is required");
        this.onFullyRested = Objects.requireNonNull(onFullyRested, "onFullyRested callback is required");
        if (regenHp < 0 || regenMana < 0 || regenMove < 0) {
            throw new IllegalArgumentException("Regen amounts must be non-negative");
        }
        this.regenHp = regenHp;
        this.regenMana = regenMana;
        this.regenMove = regenMove;
    }

    @Override
    public void tick() {
        Player player = playerSupplier.get();
        if (player == null || !player.isResting() || player.isDead()) {
            return;
        }

        PlayerVitals vitals = player.getVitals();
        if (vitals.isFull()) {
            // Already full — auto-stop rest.
            Player woken = player.withResting(false);
            onFullyRested.accept("You feel fully rested.", woken);
            return;
        }

        PlayerVitals regenned = vitals.regenRest(regenHp, regenMana, regenMove);
        Player updated = player.withVitals(regenned);

        if (regenned.isFull()) {
            // Just became full — update vitals and then auto-stop rest.
            Player woken = updated.withResting(false);
            onFullyRested.accept("You feel fully rested.", woken);
        } else {
            playerUpdater.accept(updated);
        }
    }
}
