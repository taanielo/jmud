package io.taanielo.jmud.core.ability;

public interface AbilityCooldownTracker {
    boolean isOnCooldown(AbilityId abilityId);

    int remainingTicks(AbilityId abilityId);

    void startCooldown(AbilityId abilityId, int ticks);
}
