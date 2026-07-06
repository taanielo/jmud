package io.taanielo.jmud.core.ability;

public enum AbilityEffectKind {
    VITALS,
    EFFECT,
    /**
     * Removes an active negative effect (e.g. poison) from the target instead of
     * applying one. Data-driven "cure" abilities use this kind with {@code effectId}
     * naming the effect to remove.
     */
    CURE
}
