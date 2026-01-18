package io.taanielo.jmud.core.ability;

public interface AbilityCooldownTracker {
    boolean isOnCooldown(String abilityId);

    int remainingTicks(String abilityId);

    void startCooldown(String abilityId, int ticks);
}
