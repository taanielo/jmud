package io.taanielo.jmud.core.world;

import io.taanielo.jmud.core.effects.EffectId;

public record ItemEffect(EffectId id, int durationTicks, ItemEffectOperation operation) {
    public ItemEffect {
        if (id == null) {
            throw new IllegalArgumentException("Effect id is required");
        }
        if (durationTicks < 0) {
            throw new IllegalArgumentException("Effect duration must be non-negative");
        }
        if (operation == null) {
            operation = ItemEffectOperation.APPLY;
        }
    }

    /**
     * Convenience constructor for effects that apply (rather than remove) the
     * referenced effect, matching historical two-argument usages.
     */
    public ItemEffect(EffectId id, int durationTicks) {
        this(id, durationTicks, ItemEffectOperation.APPLY);
    }
}
