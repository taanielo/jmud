package io.taanielo.jmud.core.ability;

public interface AbilityEffectResolver {
    void apply(AbilityEffect effect, AbilityContext context);
}
