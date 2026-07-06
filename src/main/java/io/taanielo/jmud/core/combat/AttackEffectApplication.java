package io.taanielo.jmud.core.combat;

import java.util.Objects;

import io.taanielo.jmud.core.effects.EffectId;

/**
 * Describes an optional status effect an {@link AttackDefinition} applies to its
 * target on a successful hit, and the percentage chance that it triggers.
 *
 * @param effectId      identifier of the effect to apply (resolved via {@code EffectEngine})
 * @param chancePercent whole-number chance (1-100 inclusive) that the effect is applied on hit
 */
public record AttackEffectApplication(EffectId effectId, int chancePercent) {

    public AttackEffectApplication {
        Objects.requireNonNull(effectId, "Effect id is required");
        if (chancePercent < 1 || chancePercent > 100) {
            throw new IllegalArgumentException("Effect application chance must be between 1 and 100");
        }
    }
}
