package io.taanielo.jmud.core.ability;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.effects.ControlType;

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
 * @param control optional crowd-control classification a {@link AbilityEffectKind#CURE} effect targets
 *                instead of a fixed {@link #effectId}. When present, the cure strips whichever active
 *                {@code ROOT}/{@code SILENCE}/{@code STUN} effect (if any) of that classification the
 *                ally currently carries, rather than a single named effect. {@code null} (omitted in
 *                data) preserves the {@code effect_id}-based cure behavior. Only meaningful on a
 *                {@code CURE} effect; ignored otherwise.
 */
public record AbilityEffect(
    AbilityEffectKind kind,
    AbilityStat stat,
    AbilityOperation operation,
    int amount,
    String effectId,
    @Nullable String damageType,
    @Nullable ControlType control
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
            requireEffectId(effectId);
        } else if (kind == AbilityEffectKind.CURE) {
            // A cure targets either a fixed effect id or a control classification; require at least one.
            if (control == null) {
                requireEffectId(effectId);
            }
        }
    }

    private static void requireEffectId(String effectId) {
        String trimmed = Objects.requireNonNull(effectId, "Effect id is required").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Effect id must not be blank");
        }
    }

    /**
     * Convenience constructor for the common untyped effect without a control classification,
     * defaulting {@link #damageType()} and {@link #control()} to {@code null}. Keeps every existing
     * caller and test source-compatible.
     */
    public AbilityEffect(
        AbilityEffectKind kind,
        AbilityStat stat,
        AbilityOperation operation,
        int amount,
        String effectId
    ) {
        this(kind, stat, operation, amount, effectId, null, null);
    }

    /**
     * Convenience constructor carrying an elemental {@code damageType} but no control classification,
     * defaulting {@link #control()} to {@code null}. Keeps existing damage-typed callers
     * source-compatible.
     */
    public AbilityEffect(
        AbilityEffectKind kind,
        AbilityStat stat,
        AbilityOperation operation,
        int amount,
        String effectId,
        @Nullable String damageType
    ) {
        this(kind, stat, operation, amount, effectId, damageType, null);
    }
}
