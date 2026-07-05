package io.taanielo.jmud.core.effects;

import java.util.List;

import io.taanielo.jmud.core.authentication.Username;

public interface EffectTarget {

    /**
     * Returns an immutable snapshot of the target's active effects.
     *
     * <p>Implementations must not return their internal mutable collection;
     * mutation goes through {@link #addEffect(EffectInstance)} and
     * {@link #removeEffect(EffectInstance)} on the tick thread only.
     */
    List<EffectInstance> effects();

    /**
     * Adds an active effect to this target. Tick-thread only (AGENTS.md §5).
     *
     * @param instance the effect instance to add
     */
    void addEffect(EffectInstance instance);

    /**
     * Removes an active effect from this target. Tick-thread only (AGENTS.md §5).
     *
     * @param instance the effect instance to remove
     * @return {@code true} if the instance was present and removed
     */
    boolean removeEffect(EffectInstance instance);

    default Username username() {
        return null;
    }

    default String displayName() {
        Username username = username();
        return username == null ? null : username.getValue();
    }
}
