package io.taanielo.jmud.core.ability;

import java.util.List;

import io.taanielo.jmud.core.messaging.MessageSpec;

public interface Ability {
    AbilityId id();

    String name();

    AbilityType type();

    int level();

    AbilityCost cost();

    AbilityCooldown cooldown();

    /**
     * Number of ticks this ability must channel before its effect resolves.
     *
     * <p>{@code 0} (the default) means the ability is instant — it resolves on the tick it is
     * invoked, exactly as every ability historically did. A positive value makes the ability a
     * <em>channeled</em> cast: the caster is visibly casting for that many ticks, cannot start
     * another ability or flee, and any damage taken interrupts the cast (see
     * {@link io.taanielo.jmud.core.ability.SpellCastState}).
     *
     * @return the channel time in ticks; {@code 0} for an instant ability
     */
    default int castTimeTicks() {
        return 0;
    }

    AbilityTargeting targeting();

    List<String> aliases();

    List<AbilityEffect> effects();

    List<MessageSpec> messages();

    default boolean canUse(AbilityContext context) {
        return true;
    }

    default void use(AbilityContext context, AbilityEffectResolver resolver) {
        for (AbilityEffect effect : effects()) {
            resolver.apply(effect, context);
        }
    }
}
