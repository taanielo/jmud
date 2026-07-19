package io.taanielo.jmud.core.server.socket;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.world.Direction;

/**
 * Tracks a single player's in-progress {@code AUTOWALK} — an automated multi-step walk toward a
 * charted area's waypoint, advancing one room per game tick.
 *
 * <p>The walk holds a precomputed queue of compass {@link Direction directions} (the pure-walking
 * route resolved by {@code WayfindService} when the command began) and a {@code stepAction} supplied
 * by the command context. Each tick the {@link PlayerTicker} calls {@link #tick()}, which runs the
 * step action; that action pops the next direction, drives it through the real movement chokepoint,
 * and cancels the walk when it completes, is blocked, or is interrupted.
 *
 * <p>This is <strong>session-transient</strong> state, modelled on
 * {@link io.taanielo.jmud.core.ability.SpellCastState}: it lives only for the duration of a login,
 * is never persisted (no save-schema change), and is dropped on logout, reconnect, and death. Every
 * method must be called on the tick thread (AGENTS.md §5); no synchronization is used because there
 * is a single writer.
 */
public final class AutoWalkState {

    private @Nullable String destinationName;
    private @Nullable Deque<Direction> remaining;
    private @Nullable Runnable stepAction;

    /**
     * Begins (or replaces) an auto-walk toward the named destination along the given route.
     *
     * @param destinationName the display name of the destination area, for status lines
     * @param route           the ordered pure-walking directions to the destination waypoint; must be
     *                        non-empty
     * @param stepAction      the per-tick action that performs one auto-walk step (pops the next
     *                        direction, moves, and cancels on completion/block/interrupt)
     */
    public void begin(String destinationName, List<Direction> route, Runnable stepAction) {
        Objects.requireNonNull(destinationName, "Destination name is required");
        Objects.requireNonNull(route, "Route is required");
        if (route.isEmpty()) {
            throw new IllegalArgumentException("Auto-walk requires a non-empty route");
        }
        this.destinationName = destinationName;
        this.remaining = new ArrayDeque<>(route);
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
     * Returns whether at least one more queued direction remains to be walked.
     *
     * @return {@code true} when another step is queued
     */
    public boolean hasNextStep() {
        return remaining != null && !remaining.isEmpty();
    }

    /**
     * Pops and returns the next queued direction. Callers must guard with {@link #hasNextStep()}.
     *
     * @return the next direction to walk
     * @throws IllegalStateException when no step is queued
     */
    public Direction nextStep() {
        if (remaining == null || remaining.isEmpty()) {
            throw new IllegalStateException("No queued auto-walk step");
        }
        return remaining.removeFirst();
    }

    /**
     * Returns the number of directions still queued (0 when not walking).
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
