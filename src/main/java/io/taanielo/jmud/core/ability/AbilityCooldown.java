package io.taanielo.jmud.core.ability;

public record AbilityCooldown(int ticks) {
    public AbilityCooldown {
        if (ticks < 0) {
            throw new IllegalArgumentException("Cooldown ticks must be non-negative");
        }
    }
}
