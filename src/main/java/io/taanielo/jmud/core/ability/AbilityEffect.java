package io.taanielo.jmud.core.ability;

public record AbilityEffect(AbilityEffectType type, int amount) {
    public AbilityEffect {
        if (type == null) {
            throw new IllegalArgumentException("Effect type is required");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Effect amount must be positive");
        }
    }
}
