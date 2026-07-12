package io.taanielo.jmud.core.ability;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.character.CharacterAttributes;
import io.taanielo.jmud.core.character.CharacterAttributesResolver;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

public class DefaultAbilityEffectResolver implements AbilityEffectResolver {

    /** Percentage-point change to spell/heal power per point of the governing attribute above 10. */
    private static final int PERCENT_PER_ATTRIBUTE_POINT = 5;

    private final EffectEngine effectEngine;
    private final AbilityMessageSink messageSink;
    private final AbilityEffectListener listener;
    private final @Nullable CharacterAttributesResolver attributesResolver;

    public DefaultAbilityEffectResolver(
        EffectEngine effectEngine,
        AbilityMessageSink messageSink,
        AbilityEffectListener listener
    ) {
        this(effectEngine, messageSink, listener, null);
    }

    /**
     * Creates a resolver that additionally scales harmful spell damage by the caster's intellect and
     * healing by the caster's wisdom (see {@link #applyVitals}). A {@code null} attributes resolver
     * disables scaling, leaving amounts unchanged.
     *
     * @param effectEngine       applies and removes status effects
     * @param messageSink        delivers ability messages to the target and room
     * @param listener           observes applied effects
     * @param attributesResolver resolves the caster's derived attributes; {@code null} disables scaling
     */
    public DefaultAbilityEffectResolver(
        EffectEngine effectEngine,
        AbilityMessageSink messageSink,
        AbilityEffectListener listener,
        @Nullable CharacterAttributesResolver attributesResolver
    ) {
        this.effectEngine = Objects.requireNonNull(effectEngine, "Effect engine is required");
        this.messageSink = Objects.requireNonNull(messageSink, "Message sink is required");
        this.listener = AbilityEffectListener.require(listener);
        this.attributesResolver = attributesResolver;
    }

    @Override
    public void apply(AbilityEffect effect, AbilityContext context) {
        Objects.requireNonNull(effect, "Ability effect is required");
        Objects.requireNonNull(context, "Ability context is required");
        switch (effect.kind()) {
            case VITALS -> {
                applyVitals(effect, context);
                listener.onApplied(effect, context);
            }
            case EFFECT -> {
                if (applyEffect(effect, context)) {
                    listener.onApplied(effect, context);
                }
            }
            case CURE -> {
                if (applyCure(effect, context)) {
                    listener.onApplied(effect, context);
                }
            }
        }
    }

    private void applyVitals(AbilityEffect effect, AbilityContext context) {
        Player target = context.target();
        PlayerVitals vitals = target.getVitals();
        int amount = effect.amount();
        int scaledHp = scaledHpAmount(effect, context.source(), amount);
        PlayerVitals updated = switch (effect.stat()) {
            case HP -> effect.operation() == AbilityOperation.INCREASE
                ? vitals.heal(scaledHp)
                : vitals.damage(scaledHp);
            case MANA -> effect.operation() == AbilityOperation.INCREASE
                ? vitals.restoreMana(amount)
                : vitals.consumeMana(amount);
            case MOVE -> effect.operation() == AbilityOperation.INCREASE
                ? vitals.restoreMove(amount)
                : vitals.consumeMove(amount);
        };
        context.updateTarget(target.withVitals(updated));
    }

    /**
     * Scales an HP ability amount by the caster's governing attribute: intellect empowers harmful
     * spell damage (HP decrease) and wisdom empowers healing (HP increase), each by
     * {@value #PERCENT_PER_ATTRIBUTE_POINT}% per attribute point above the baseline (a floor is taken
     * on the result). Amounts are returned unchanged when no attributes resolver is configured. Only
     * HP effects scale; mana and move restoration/drain are unaffected.
     */
    private int scaledHpAmount(AbilityEffect effect, Player caster, int amount) {
        if (attributesResolver == null || effect.stat() != AbilityStat.HP) {
            return amount;
        }
        CharacterAttributes attributes =
            attributesResolver.resolve(caster.getRace(), caster.getClassId(), caster.getLevel());
        int modifier = effect.operation() == AbilityOperation.INCREASE
            ? attributes.wisdomModifier()
            : attributes.intellectModifier();
        int percent = 100 + (PERCENT_PER_ATTRIBUTE_POINT * modifier);
        if (percent < 0) {
            percent = 0;
        }
        return (int) ((long) amount * percent / 100);
    }

    private boolean applyEffect(AbilityEffect effect, AbilityContext context) {
        io.taanielo.jmud.core.effects.EffectMessageSink sink = effectMessageSink(context);
        try {
            return effectEngine.apply(context.target(), EffectId.of(effect.effectId()), sink);
        } catch (EffectRepositoryException e) {
            throw new IllegalStateException("Failed to apply effect " + effect.effectId() + ": " + e.getMessage(), e);
        }
    }

    private boolean applyCure(AbilityEffect effect, AbilityContext context) {
        io.taanielo.jmud.core.effects.EffectMessageSink sink = effectMessageSink(context);
        try {
            return effectEngine.remove(context.target(), EffectId.of(effect.effectId()), sink);
        } catch (EffectRepositoryException e) {
            throw new IllegalStateException("Failed to remove effect " + effect.effectId() + ": " + e.getMessage(), e);
        }
    }

    private io.taanielo.jmud.core.effects.EffectMessageSink effectMessageSink(AbilityContext context) {
        return new io.taanielo.jmud.core.effects.EffectMessageSink() {
            @Override
            public void sendToTarget(String message) {
                messageSink.sendToTarget(context.target(), message);
            }

            @Override
            public void sendToRoom(String message) {
                messageSink.sendToRoom(context.source(), context.target(), message);
            }
        };
    }
}
