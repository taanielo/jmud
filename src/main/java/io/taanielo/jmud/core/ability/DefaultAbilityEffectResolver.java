package io.taanielo.jmud.core.ability;

import java.util.Objects;

import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectMessageSink;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

public class DefaultAbilityEffectResolver implements AbilityEffectResolver {
    private final EffectEngine effectEngine;
    private final EffectMessageSink effectMessageSink;

    public DefaultAbilityEffectResolver(EffectEngine effectEngine, EffectMessageSink effectMessageSink) {
        this.effectEngine = Objects.requireNonNull(effectEngine, "Effect engine is required");
        this.effectMessageSink = Objects.requireNonNull(effectMessageSink, "Effect message sink is required");
    }

    @Override
    public void apply(AbilityEffect effect, AbilityContext context) {
        Objects.requireNonNull(effect, "Ability effect is required");
        Objects.requireNonNull(context, "Ability context is required");
        if (effect.kind() == AbilityEffectKind.VITALS) {
            applyVitals(effect, context);
            return;
        }
        applyEffect(effect, context);
    }

    private void applyVitals(AbilityEffect effect, AbilityContext context) {
        Player target = context.target();
        PlayerVitals vitals = target.getVitals();
        PlayerVitals updated = switch (effect.stat()) {
            case HP -> effect.operation() == AbilityOperation.INCREASE
                ? vitals.heal(effect.amount())
                : vitals.damage(effect.amount());
            case MANA -> effect.operation() == AbilityOperation.INCREASE
                ? vitals.restoreMana(effect.amount())
                : vitals.consumeMana(effect.amount());
            case MOVE -> effect.operation() == AbilityOperation.INCREASE
                ? vitals.restoreMove(effect.amount())
                : vitals.consumeMove(effect.amount());
        };
        context.updateTarget(target.withVitals(updated));
    }

    private void applyEffect(AbilityEffect effect, AbilityContext context) {
        try {
            effectEngine.apply(context.target(), EffectId.of(effect.effectId()), effectMessageSink);
        } catch (EffectRepositoryException e) {
            throw new IllegalStateException("Failed to apply effect " + effect.effectId() + ": " + e.getMessage(), e);
        }
    }
}
