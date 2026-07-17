package io.taanielo.jmud.core.ability;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A single effect an ability applies when used.
 *
 * @param damageType optional elemental damage-type tag for a damaging {@link AbilityEffectKind#VITALS}
 *                  effect, carried as a raw case-insensitive string (e.g. {@code "FIRE"}/{@code "COLD"}/
 *                  {@code "POISON"}), mirroring the {@link #effectId} raw-string convention rather than
 *                  importing {@code io.taanielo.jmud.core.combat.DamageType} into the ability layer.
 *                  {@code null} (omitted in data) means untyped/physical: never resisted or amplified by
 *                  a mob's elemental resistance/vulnerability. Only meaningful on a {@code VITALS} HP
 *                  decrease; ignored otherwise.
 */
public record AbilityEffect(
    AbilityEffectKind kind,
    AbilityStat stat,
    AbilityOperation operation,
    int amount,
    String effectId,
    @Nullable String damageType
) {
    public AbilityEffect {
        Objects.requireNonNull(kind, "Effect kind is required");
        if (kind == AbilityEffectKind.VITALS) {
            Objects.requireNonNull(stat, "Effect stat is required");
            Objects.requireNonNull(operation, "Effect operation is required");
            if (amount <= 0) {
                throw new IllegalArgumentException("Effect amount must be positive");
            }
        } else if (kind == AbilityEffectKind.EFFECT || kind == AbilityEffectKind.CURE) {
            String trimmed = Objects.requireNonNull(effectId, "Effect id is required").trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Effect id must not be blank");
            }
        }
    }

    /**
     * Convenience constructor for the common untyped effect, defaulting {@link #damageType()} to
     * {@code null} (physical/untyped). Keeps every existing caller and test source-compatible.
     */
    public AbilityEffect(
        AbilityEffectKind kind,
        AbilityStat stat,
        AbilityOperation operation,
        int amount,
        String effectId
    ) {
        this(kind, stat, operation, amount, effectId, null);
    }
}
