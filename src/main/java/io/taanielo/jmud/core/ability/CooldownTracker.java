package io.taanielo.jmud.core.ability;

import java.util.Objects;

import io.taanielo.jmud.core.tick.system.CooldownSystem;

public class CooldownTracker implements AbilityCooldownTracker {
    private final CooldownSystem cooldownSystem;

    public CooldownTracker(CooldownSystem cooldownSystem) {
        this.cooldownSystem = Objects.requireNonNull(cooldownSystem, "Cooldown system is required");
    }

    @Override
    public boolean isOnCooldown(String abilityId) {
        return cooldownSystem.isOnCooldown(abilityId);
    }

    @Override
    public int remainingTicks(String abilityId) {
        return cooldownSystem.remainingTicks(abilityId);
    }

    @Override
    public void startCooldown(String abilityId, int ticks) {
        cooldownSystem.register(abilityId, ticks);
    }
}
