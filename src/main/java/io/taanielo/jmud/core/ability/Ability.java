package io.taanielo.jmud.core.ability;

import java.util.List;

public interface Ability {
    AbilityId id();

    String name();

    AbilityType type();

    int level();

    AbilityCost cost();

    AbilityCooldown cooldown();

    AbilityTargeting targeting();

    List<String> aliases();

    List<AbilityEffect> effects();

    default boolean canUse(AbilityContext context) {
        return true;
    }

    default void use(AbilityContext context, AbilityEffectResolver resolver) {
        for (AbilityEffect effect : effects()) {
            resolver.apply(effect, context);
        }
    }
}
