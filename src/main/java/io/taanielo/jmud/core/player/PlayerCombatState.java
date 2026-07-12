package io.taanielo.jmud.core.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.effects.EffectInstance;

/**
 * Holds a player's combat-related state: vitals, active effects, death flag and the
 * transient stealth flag.
 *
 * <p>The effects collection is owned by this class. Callers observe it through the
 * immutable snapshot returned by {@link #effects()} and mutate it only through
 * {@link #addEffect(EffectInstance)} and {@link #removeEffect(EffectInstance)},
 * which — like all game-state mutation — must run on the tick thread (AGENTS.md §5).
 *
 * <p>The {@link #stealthActive()} flag and the {@link #mount()} ridden-state are in-memory only and
 * never serialised; they default to "off"/"dismounted" for players loaded from persistence and are
 * cleared on death and respawn.
 */
public class PlayerCombatState {
    private final PlayerVitals vitals;
    private final List<EffectInstance> effects;
    private final boolean dead;
    private final boolean stealthActive;
    private final PlayerMount mount;

    public PlayerCombatState(PlayerVitals vitals, List<EffectInstance> effects, Boolean dead) {
        this(vitals, effects, dead, false);
    }

    public PlayerCombatState(PlayerVitals vitals, List<EffectInstance> effects, Boolean dead, boolean stealthActive) {
        this(vitals, effects, dead, stealthActive, PlayerMount.dismounted());
    }

    public PlayerCombatState(PlayerVitals vitals, List<EffectInstance> effects, Boolean dead, boolean stealthActive,
                             PlayerMount mount) {
        this.vitals = Objects.requireNonNull(vitals, "Vitals are required");
        this.effects = new ArrayList<>(Objects.requireNonNullElse(effects, List.of()));
        boolean resolvedDead = Objects.requireNonNullElse(dead, false) || vitals.hp() <= 0;
        this.dead = resolvedDead;
        this.stealthActive = stealthActive;
        this.mount = Objects.requireNonNullElse(mount, PlayerMount.dismounted());
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

    /**
     * Returns whether the player is currently in stealth (hidden). In-memory only, never persisted.
     */
    public boolean stealthActive() {
        return stealthActive;
    }

    /**
     * Returns the player's transient ridden-mount state. In-memory only, never persisted.
     */
    public PlayerMount mount() {
        return mount;
    }

    public PlayerCombatState die() {
        if (dead && vitals.hp() <= 0 && effects.isEmpty() && !stealthActive && !mount.isMounted()) {
            return this;
        }
        PlayerVitals deadVitals = vitals.damage(vitals.hp());
        return new PlayerCombatState(deadVitals, List.of(), true, false, PlayerMount.dismounted());
    }

    public PlayerCombatState respawn() {
        PlayerVitals restored = vitals.respawnHalf();
        return new PlayerCombatState(restored, List.of(), false, false, PlayerMount.dismounted());
    }

    public PlayerCombatState withoutEffects() {
        return new PlayerCombatState(vitals, List.of(), dead, stealthActive, mount);
    }

    public PlayerCombatState withVitals(PlayerVitals updatedVitals) {
        return new PlayerCombatState(updatedVitals, effects, dead, stealthActive, mount);
    }

    public PlayerCombatState withDead(boolean nextDead) {
        return new PlayerCombatState(vitals, effects, nextDead, stealthActive, mount);
    }

    /**
     * Returns a copy of this combat state with the stealth flag set to the given value.
     *
     * @param active {@code true} to enter stealth, {@code false} to leave it
     */
    public PlayerCombatState withStealth(boolean active) {
        if (this.stealthActive == active) {
            return this;
        }
        return new PlayerCombatState(vitals, effects, dead, active, mount);
    }

    /**
     * Returns a copy of this combat state with the ridden-mount state set to the given value.
     *
     * @param newMount the new mount state; must not be null (use {@link PlayerMount#dismounted()} to
     *                 clear)
     */
    public PlayerCombatState withMount(PlayerMount newMount) {
        Objects.requireNonNull(newMount, "Mount is required");
        if (this.mount.equals(newMount)) {
            return this;
        }
        return new PlayerCombatState(vitals, effects, dead, stealthActive, newMount);
    }
}
