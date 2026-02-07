package io.taanielo.jmud.core.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.effects.EffectInstance;

public class PlayerCombatState {
    private final PlayerVitals vitals;
    private final List<EffectInstance> effects;
    private final boolean dead;

    public PlayerCombatState(PlayerVitals vitals, List<EffectInstance> effects, Boolean dead) {
        this.vitals = Objects.requireNonNull(vitals, "Vitals are required");
        this.effects = new ArrayList<>(Objects.requireNonNullElse(effects, List.of()));
        boolean resolvedDead = Objects.requireNonNullElse(dead, false) || vitals.hp() <= 0;
        this.dead = resolvedDead;
    }

    public PlayerVitals vitals() {
        return vitals;
    }

    public List<EffectInstance> effects() {
        return effects;
    }

    public boolean dead() {
        return dead;
    }

    public PlayerCombatState die() {
        if (dead && vitals.hp() <= 0 && effects.isEmpty()) {
            return this;
        }
        PlayerVitals deadVitals = vitals.damage(vitals.hp());
        return new PlayerCombatState(deadVitals, List.of(), true);
    }

    public PlayerCombatState respawn() {
        PlayerVitals restored = vitals.respawnHalf();
        return new PlayerCombatState(restored, List.of(), false);
    }

    public PlayerCombatState withoutEffects() {
        return new PlayerCombatState(vitals, List.of(), dead);
    }

    public PlayerCombatState withVitals(PlayerVitals updatedVitals) {
        return new PlayerCombatState(updatedVitals, effects, dead);
    }

    public PlayerCombatState withDead(boolean nextDead) {
        return new PlayerCombatState(vitals, effects, nextDead);
    }
}
