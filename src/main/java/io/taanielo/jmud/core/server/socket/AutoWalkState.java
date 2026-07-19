package io.taanielo.jmud.core.server.socket;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.world.area.WayfindService.AutoWalkStep;

/**
 * Tracks a single player's in-progress {@code AUTOWALK} — an automated multi-step journey toward a
 * charted area's waypoint, advancing one action per game tick.
 *
 * <p>The journey holds a precomputed queue of {@link AutoWalkStep steps} (the route resolved by
 * {@code WayfindService} when the command began) and a {@code stepAction} supplied by the command
 * context. Each step is either a walked compass move or a single ferry leg (board the deck, wait for
 * the scheduled departure, ride to the arrival dock). Each tick the {@link PlayerTicker} calls
 * {@link #tick()}, which runs the step action; that action peeks the head step, performs one tick's
 * worth of it through the real movement chokepoint, and only pops it once the step is complete —
 * cancelling the journey when it finishes, is blocked, or is interrupted.
 *
 * <p>This is <strong>session-transient</strong> state, modelled on
 * {@link io.taanielo.jmud.core.ability.SpellCastState}: it lives only for the duration of a login,
 * is never persisted (no save-schema change), and is dropped on logout, reconnect, and death. Every
 * method must be called on the tick thread (AGENTS.md §5); no synchronization is used because there
 * is a single writer.
 */
public final class AutoWalkState {

    private @Nullable String destinationName;
    private @Nullable Deque<AutoWalkStep> remaining;
    private @Nullable Runnable stepAction;

    /**
     * Begins (or replaces) an auto-walk toward the named destination along the given route.
     *
     * @param destinationName the display name of the destination area, for status lines
     * @param steps           the ordered steps to the destination waypoint (walked moves plus at most
     *                        one ferry leg); must be non-empty
     * @param stepAction      the per-tick action that performs one tick's worth of the head step (moves
     *                        or waits, pops completed steps, and cancels on completion/block/interrupt)
     */
    public void begin(String destinationName, List<AutoWalkStep> steps, Runnable stepAction) {
        Objects.requireNonNull(destinationName, "Destination name is required");
        Objects.requireNonNull(steps, "Steps are required");
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("Auto-walk requires a non-empty route");
        }
        this.destinationName = destinationName;
        this.remaining = new ArrayDeque<>(steps);
        this.stepAction = Objects.requireNonNull(stepAction, "Step action is required");
    }

    /**
     * Returns whether an auto-walk is currently in progress.
     *
     * @return {@code true} while the player is auto-walking
     */
    public boolean isWalking() {
        return stepAction != null;
    }

    /**
     * Advances the in-progress walk by one tick by running the step action. No-op when not walking.
     */
    public void tick() {
        Runnable action = stepAction;
        if (action != null) {
            action.run();
        }
    }

    /**
     * Returns the destination area's display name, or {@code null} when not walking.
     *
     * @return the destination name, or {@code null}
     */
    public @Nullable String destinationName() {
        return destinationName;
    }

    /**
     * Returns whether at least one more queued step remains to be executed.
     *
     * @return {@code true} when another step is queued
     */
    public boolean hasNextStep() {
        return remaining != null && !remaining.isEmpty();
    }

    /**
     * Returns the next queued step without removing it. Callers must guard with {@link #hasNextStep()}.
     *
     * @return the next step to execute
     * @throws IllegalStateException when no step is queued
     */
    public AutoWalkStep peekNextStep() {
        if (remaining == null || remaining.isEmpty()) {
            throw new IllegalStateException("No queued auto-walk step");
        }
        return remaining.peekFirst();
    }

    /**
     * Removes and returns the head step, marking it complete. Callers must guard with
     * {@link #hasNextStep()}.
     *
     * @return the completed step
     * @throws IllegalStateException when no step is queued
     */
    public AutoWalkStep advanceStep() {
        if (remaining == null || remaining.isEmpty()) {
            throw new IllegalStateException("No queued auto-walk step");
        }
        return remaining.removeFirst();
    }

    /**
     * Returns the number of steps still queued (0 when not walking).
     *
     * @return the remaining step count
     */
    public int remainingSteps() {
        return remaining == null ? 0 : remaining.size();
    }

    /**
     * Cancels the in-progress walk, clearing all state. Safe to call when not walking (no-op), so it
     * can be invoked unconditionally from teardown paths (death, logout, reconnect).
     */
    public void cancel() {
        destinationName = null;
        remaining = null;
        stepAction = null;
    }
}
