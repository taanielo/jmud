package io.taanielo.jmud.core.ability;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Tracks a single player's in-progress <em>channeled</em> ("cast time") spell.
 *
 * <p>Channeled spells (those whose {@link Ability#castTimeTicks()} is positive) do not resolve on
 * the tick they are invoked. Instead a pending cast is recorded here and counted down one tick at a
 * time. When the countdown reaches zero the {@code onComplete} action runs (which resolves the
 * spell's effect and spends its cost); if the caster takes damage first, {@link #interrupt()} runs
 * the {@code onInterrupt} action instead and the effect never applies.
 *
 * <p>This is <strong>session-transient</strong> state: it lives only for the duration of a login,
 * is never persisted, and is dropped on logout, reconnect, and death. Every method must be called on
 * the tick thread (AGENTS.md §5); no synchronization is used because there is a single writer.
 *
 * <p>The cost is <em>deferred</em>, not reserved: nothing is charged when a cast begins, and the
 * {@code onComplete} action performs the normal ability resolution (which spends the cost). An
 * interrupted or cancelled cast therefore never charges the caster — observationally identical to a
 * full refund, and it makes "mana isn't spent on a fizzle" impossible to violate.
 */
public final class SpellCastState {

    private @Nullable AbilityId abilityId;
    private @Nullable String abilityName;
    private int ticksRemaining;
    private @Nullable Runnable onComplete;
    private @Nullable Runnable onInterrupt;

    /**
     * Begins channeling an ability, replacing any cast already in progress (callers should refuse a
     * new cast while {@link #isCasting()} rather than relying on this to stack).
     *
     * @param abilityId     the channeled ability's id
     * @param abilityName   the channeled ability's display name, for status lines
     * @param castTimeTicks the number of ticks to channel; must be positive
     * @param onComplete    action run on the tick the countdown reaches zero (resolves the spell)
     * @param onInterrupt   action run if the cast is interrupted (message + short cooldown)
     */
    public void begin(
        AbilityId abilityId,
        String abilityName,
        int castTimeTicks,
        Runnable onComplete,
        Runnable onInterrupt
    ) {
        if (castTimeTicks <= 0) {
            throw new IllegalArgumentException("Channeled cast requires a positive cast time");
        }
        this.abilityId = Objects.requireNonNull(abilityId, "Ability id is required");
        this.abilityName = Objects.requireNonNull(abilityName, "Ability name is required");
        this.ticksRemaining = castTimeTicks;
        this.onComplete = Objects.requireNonNull(onComplete, "Completion action is required");
        this.onInterrupt = Objects.requireNonNull(onInterrupt, "Interrupt action is required");
    }

    /**
     * Returns whether a channeled cast is currently in progress.
     *
     * @return {@code true} while a spell is being channeled
     */
    public boolean isCasting() {
        return abilityId != null;
    }

    /**
     * Advances the in-progress cast by one tick. When the countdown reaches zero the state is
     * cleared <em>before</em> the completion action runs, so resolving the spell (which may itself
     * touch this session) observes an idle cast state. No-op when nothing is being cast.
     */
    public void tick() {
        if (!isCasting()) {
            return;
        }
        ticksRemaining--;
        if (ticksRemaining <= 0) {
            Runnable completion = onComplete;
            clear();
            if (completion != null) {
                completion.run();
            }
        }
    }

    /**
     * Interrupts the in-progress cast: clears the pending state and runs the interrupt action (which
     * warns the caster and puts the ability on a short cooldown). No-op when nothing is being cast, so
     * it is safe to call from any damage path unconditionally.
     */
    public void interrupt() {
        if (!isCasting()) {
            return;
        }
        Runnable interruption = onInterrupt;
        clear();
        if (interruption != null) {
            interruption.run();
        }
    }

    /**
     * Cancels the in-progress cast without running either callback. Used for teardown (death, logout,
     * reconnect) where no interruption message or cooldown is appropriate.
     */
    public void cancelSilently() {
        clear();
    }

    /**
     * Returns the id of the ability currently being channeled, or {@code null} when idle.
     *
     * @return the channeling ability id, or {@code null}
     */
    public @Nullable AbilityId castingAbilityId() {
        return abilityId;
    }

    /**
     * Returns the display name of the ability currently being channeled, or {@code null} when idle.
     *
     * @return the channeling ability name, or {@code null}
     */
    public @Nullable String castingAbilityName() {
        return abilityName;
    }

    /**
     * Returns the number of ticks left before the in-progress cast resolves, or {@code 0} when idle.
     *
     * @return the remaining channel ticks
     */
    public int ticksRemaining() {
        return ticksRemaining;
    }

    private void clear() {
        abilityId = null;
        abilityName = null;
        ticksRemaining = 0;
        onComplete = null;
        onInterrupt = null;
    }
}
