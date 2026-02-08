package io.taanielo.jmud.core.ability;

import java.util.Objects;

import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

public class DefaultAbilityEffectResolver implements AbilityEffectResolver {
    private final EffectEngine effectEngine;
    private final AbilityMessageSink messageSink;
    private final AbilityEffectListener listener;

    public DefaultAbilityEffectResolver(
        EffectEngine effectEngine,
        AbilityMessageSink messageSink,
        AbilityEffectListener listener
    ) {
        this.effectEngine = Objects.requireNonNull(effectEngine, "Effect engine is required");
        this.messageSink = Objects.requireNonNull(messageSink, "Message sink is required");
        this.listener = AbilityEffectListener.require(listener);
    }

    @Override
    public void apply(AbilityEffect effect, AbilityContext context) {
        Objects.requireNonNull(effect, "Ability effect is required");
        Objects.requireNonNull(context, "Ability context is required");
        if (effect.kind() == AbilityEffectKind.VITALS) {
            applyVitals(effect, context);
            listener.onApplied(effect, context);
            return;
        }
        if (applyEffect(effect, context)) {
            listener.onApplied(effect, context);
        }
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

    private boolean applyEffect(AbilityEffect effect, AbilityContext context) {
        io.taanielo.jmud.core.effects.EffectMessageSink sink = new io.taanielo.jmud.core.effects.EffectMessageSink() {
            @Override
            public void sendToTarget(String message) {
                messageSink.sendToTarget(context.target(), message);
            }

            @Override
            public void sendToRoom(String message) {
                messageSink.sendToRoom(context.source(), context.target(), message);
            }
        };
        try {
            return effectEngine.apply(context.target(), EffectId.of(effect.effectId()), sink);
        } catch (EffectRepositoryException e) {
            throw new IllegalStateException("Failed to apply effect " + effect.effectId() + ": " + e.getMessage(), e);
        }
    }
}
