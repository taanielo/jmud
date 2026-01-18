package io.taanielo.jmud.core.ability;

import java.util.Objects;

import io.taanielo.jmud.core.tick.system.CooldownSystem;

public class CooldownTracker implements AbilityCooldownTracker {
    private final CooldownSystem cooldownSystem;

    public CooldownTracker(CooldownSystem cooldownSystem) {
        this.cooldownSystem = Objects.requireNonNull(cooldownSystem, "Cooldown system is required");
    }

    @Override
    public boolean isOnCooldown(AbilityId abilityId) {
        return cooldownSystem.isOnCooldown(abilityId.getValue());
    }

    @Override
    public int remainingTicks(AbilityId abilityId) {
        return cooldownSystem.remainingTicks(abilityId.getValue());
    }

    @Override
    public void startCooldown(AbilityId abilityId, int ticks) {
        cooldownSystem.register(abilityId.getValue(), ticks);
    }
}
