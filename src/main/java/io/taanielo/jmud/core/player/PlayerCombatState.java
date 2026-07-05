package io.taanielo.jmud.core.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.effects.EffectInstance;

/**
 * Holds a player's combat-related state: vitals, active effects and death flag.
 *
 * <p>The effects collection is owned by this class. Callers observe it through the
 * immutable snapshot returned by {@link #effects()} and mutate it only through
 * {@link #addEffect(EffectInstance)} and {@link #removeEffect(EffectInstance)},
 * which — like all game-state mutation — must run on the tick thread (AGENTS.md §5).
 */
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

    /**
     * Returns an immutable snapshot of the currently active effects.
     *
     * <p>The returned list never reflects later mutations and throws
     * {@link UnsupportedOperationException} on any modification attempt. Use
     * {@link #addEffect(EffectInstance)} / {@link #removeEffect(EffectInstance)}
     * to change the active effects.
     *
     * @return an unmodifiable copy of the active effects
     */
    public List<EffectInstance> effects() {
        return List.copyOf(effects);
    }

    /**
     * Adds an active effect. Tick-thread only (AGENTS.md §5).
     *
     * @param instance the effect instance to add
     */
    public void addEffect(EffectInstance instance) {
        effects.add(Objects.requireNonNull(instance, "Effect instance is required"));
    }

    /**
     * Removes an active effect. Tick-thread only (AGENTS.md §5).
     *
     * @param instance the effect instance to remove
     * @return {@code true} if the instance was present and removed
     */
    public boolean removeEffect(EffectInstance instance) {
        return effects.remove(Objects.requireNonNull(instance, "Effect instance is required"));
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
