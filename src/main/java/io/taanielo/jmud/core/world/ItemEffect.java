package io.taanielo.jmud.core.world;

import io.taanielo.jmud.core.effects.EffectId;

public record ItemEffect(EffectId id, int durationTicks) {
    public ItemEffect {
        if (id == null) {
            throw new IllegalArgumentException("Effect id is required");
        }
        if (durationTicks < 0) {
            throw new IllegalArgumentException("Effect duration must be non-negative");
        }
    }
}
