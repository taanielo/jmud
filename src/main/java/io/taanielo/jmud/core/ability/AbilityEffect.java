package io.taanielo.jmud.core.ability;

import java.util.Objects;

public record AbilityEffect(
    AbilityEffectKind kind,
    AbilityStat stat,
    AbilityOperation operation,
    int amount,
    String effectId
) {
    public AbilityEffect {
        Objects.requireNonNull(kind, "Effect kind is required");
        if (kind == AbilityEffectKind.VITALS) {
            Objects.requireNonNull(stat, "Effect stat is required");
            Objects.requireNonNull(operation, "Effect operation is required");
            if (amount <= 0) {
                throw new IllegalArgumentException("Effect amount must be positive");
            }
        } else if (kind == AbilityEffectKind.EFFECT) {
            String trimmed = Objects.requireNonNull(effectId, "Effect id is required").trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Effect id must not be blank");
            }
        }
    }
}
