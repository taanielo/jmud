package io.taanielo.jmud.core.server.socket;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.ability.SpellCastState;
import io.taanielo.jmud.core.effects.PlayerEffectTicker;
import io.taanielo.jmud.core.healing.PlayerHealingTicker;
import io.taanielo.jmud.core.player.PlayerRespawnTicker;
import io.taanielo.jmud.core.player.RestingTicker;
import io.taanielo.jmud.core.player.SustenanceTicker;
import io.taanielo.jmud.core.tick.Tickable;
import io.taanielo.jmud.core.tick.system.CooldownSystem;

/**
 * Composed per-player {@link Tickable} that runs all player-scoped tick stages
 * in a fixed, deterministic order within each tick.
 *
 * <p>Stage execution order (matches the former separate-subscription order):
 * <ol>
 *   <li><b>Spell cast</b> — counts down an in-progress channeled cast and resolves it when the
 *       countdown reaches zero (see {@link SpellCastState})</li>
 *   <li><b>Command queue</b> — executes enqueued player commands (bounded per tick,
 *       see {@link PlayerCommandQueue})</li>
 *   <li><b>Auto-walk</b> — advances an in-progress {@code AUTOWALK} by one step, after the command
 *       drain so a manual command this tick cancels the walk first (see {@link AutoWalkState})</li>
 *   <li><b>Ability cooldowns</b> — decrements all in-flight ability cooldown counters</li>
 *   <li><b>Respawn</b> — counts down the respawn timer and triggers respawn when due</li>
 *   <li><b>Effects</b> — applies per-tick effect events (only when enabled via
 *       {@link #enableEffects})</li>
 *   <li><b>Healing</b> — applies per-tick HP regeneration (only when enabled via
 *       {@link #enableHealing})</li>
 *   <li><b>Resting</b> — applies per-tick rest regeneration (only while resting is
 *       active; toggled via {@link #enableResting}/{@link #disableResting})</li>
 *   <li><b>Sustenance</b> — decays hunger/thirst each tick (only when enabled via
 *       {@link #enableSustenance})</li>
 * </ol>
 *
 * <p>Global tickables ({@code MobRegistry}, {@code CorpseDecayTicker}, {@code TickClock},
 * etc.) run interleaved with this ticker in whatever order they were registered in
 * {@link io.taanielo.jmud.core.tick.TickRegistry}.
 *
 * <p>Thread safety: all mutating calls ({@code enable*}/{@code disable*}) must occur on
 * the tick thread (AGENTS.md §5). The {@link PlayerCommandQueue} is the only stage designed
 * for cross-thread interaction; reader threads enqueue via
 * {@link PlayerCommandQueue#enqueue(Runnable)}.
 */
public class PlayerTicker implements Tickable {

    private final PlayerCommandQueue commandQueue;
    private final CooldownSystem abilityCooldowns;
    private final PlayerRespawnTicker respawnTicker;
    private final SpellCastState spellCastState;
    private final AutoWalkState autoWalkState;

    /** Stage 4: effect ticking, enabled after login. */
    @Nullable
    private PlayerEffectTicker effectTicker;

    /** Stage 5: healing ticking, enabled after login. */
    @Nullable
    private PlayerHealingTicker healingTicker;

    /** Stage 6: rest regeneration ticking, toggled by REST/WAKE commands. */
    @Nullable
    private RestingTicker restingTicker;

    /** Stage 7: hunger/thirst decay ticking, enabled after login. */
    @Nullable
    private SustenanceTicker sustenanceTicker;

    /**
     * Constructs a {@code PlayerTicker} with the three always-active stages.
     * Effect, healing, and resting stages are initially disabled; enable them
     * via the corresponding {@code enable*} methods after login or command.
     *
     * @param commandQueue     drains player commands each tick; must not be null
     * @param abilityCooldowns decrements ability cooldown counters each tick; must not be null
     * @param respawnTicker    manages respawn countdown; must not be null
     * @param spellCastState   counts down an in-progress channeled cast each tick; must not be null
     * @param autoWalkState    advances an in-progress auto-walk one step each tick; must not be null
     */
    public PlayerTicker(
        PlayerCommandQueue commandQueue,
        CooldownSystem abilityCooldowns,
        PlayerRespawnTicker respawnTicker,
        SpellCastState spellCastState,
        AutoWalkState autoWalkState
    ) {
        this.commandQueue = Objects.requireNonNull(commandQueue, "Command queue is required");
        this.abilityCooldowns = Objects.requireNonNull(abilityCooldowns, "Ability cooldowns is required");
        this.respawnTicker = Objects.requireNonNull(respawnTicker, "Respawn ticker is required");
        this.spellCastState = Objects.requireNonNull(spellCastState, "Spell cast state is required");
        this.autoWalkState = Objects.requireNonNull(autoWalkState, "Auto-walk state is required");
    }

    /**
     * Runs all active stages in order. Called once per game tick on the tick thread.
     */
    @Override
    public void tick() {
        // Stage 0: advance any in-progress channeled cast. Runs before the command drain so a cast
        // begun this tick is not decremented until the following tick, and so a cast that completes
        // resolves before the caster's new commands for this tick are processed.
        spellCastState.tick();
        // Stage 1: drain and execute player commands
        commandQueue.tick();
        // Stage 1b: advance an in-progress auto-walk by one step. Runs after the command drain so a
        // manual command dispatched this tick (which cancels the walk) always wins over a queued step
        // — no autopilot step executes after the player takes manual control (issue #767).
        autoWalkState.tick();
        // Stage 2: decrement ability cooldowns
        abilityCooldowns.tick();
        // Stage 3: respawn countdown / trigger
        respawnTicker.tick();
        // Stage 4: per-tick effect events (conditional)
        if (effectTicker != null) {
            effectTicker.tick();
        }
        // Stage 5: per-tick healing (conditional)
        if (healingTicker != null) {
            healingTicker.tick();
        }
        // Stage 6: rest regeneration (conditional)
        if (restingTicker != null) {
            restingTicker.tick();
        }
        // Stage 7: hunger/thirst decay (conditional)
        if (sustenanceTicker != null) {
            sustenanceTicker.tick();
        }
    }

    // ── Stage 4: effects ──────────────────────────────────────────────────────

    /**
     * Enables the effect-tick stage for the current player. Replaces any
     * previously set ticker. Must be called on the tick thread (or before the
     * first tick, e.g. during login setup).
     *
     * @param ticker the effect ticker to activate; must not be null
     */
    public void enableEffects(PlayerEffectTicker ticker) {
        this.effectTicker = Objects.requireNonNull(ticker, "Effect ticker is required");
    }

    /**
     * Disables the effect-tick stage. Must be called on the tick thread.
     */
    public void disableEffects() {
        this.effectTicker = null;
    }

    /**
     * Returns {@code true} when the effects stage is currently active.
     */
    public boolean isEffectsEnabled() {
        return effectTicker != null;
    }

    // ── Stage 5: healing ─────────────────────────────────────────────────────

    /**
     * Enables the healing-tick stage. Replaces any previously set ticker.
     * Must be called on the tick thread (or during login setup).
     *
     * @param ticker the healing ticker to activate; must not be null
     */
    public void enableHealing(PlayerHealingTicker ticker) {
        this.healingTicker = Objects.requireNonNull(ticker, "Healing ticker is required");
    }

    /**
     * Disables the healing-tick stage. Must be called on the tick thread.
     */
    public void disableHealing() {
        this.healingTicker = null;
    }

    /**
     * Returns {@code true} when the healing stage is currently active.
     */
    public boolean isHealingEnabled() {
        return healingTicker != null;
    }

    // ── Stage 6: resting ─────────────────────────────────────────────────────

    /**
     * Enables the resting-tick stage with the given ticker. Replaces any
     * previously active resting ticker. Typically called by the REST command.
     * Must be called on the tick thread.
     *
     * @param ticker the resting ticker to activate; must not be null
     */
    public void enableResting(RestingTicker ticker) {
        this.restingTicker = Objects.requireNonNull(ticker, "Resting ticker is required");
    }

    /**
     * Disables the resting-tick stage. Typically called by the WAKE command or
     * when a mob interrupts rest. Must be called on the tick thread.
     */
    public void disableResting() {
        this.restingTicker = null;
    }

    /**
     * Returns {@code true} when the resting stage is currently active.
     */
    public boolean isRestingEnabled() {
        return restingTicker != null;
    }

    // ── Stage 7: sustenance ──────────────────────────────────────────────────

    /**
     * Enables the sustenance-decay stage. Replaces any previously set ticker.
     * Must be called on the tick thread (or during login setup).
     *
     * @param ticker the sustenance ticker to activate; must not be null
     */
    public void enableSustenance(SustenanceTicker ticker) {
        this.sustenanceTicker = Objects.requireNonNull(ticker, "Sustenance ticker is required");
    }

    /**
     * Disables the sustenance-decay stage. Must be called on the tick thread.
     */
    public void disableSustenance() {
        this.sustenanceTicker = null;
    }

    /**
     * Returns {@code true} when the sustenance stage is currently active.
     */
    public boolean isSustenanceEnabled() {
        return sustenanceTicker != null;
    }

    /**
     * Returns the total number of commands pending in this player's command queue.
     * Used by metrics infrastructure to aggregate across all connected players.
     *
     * @return current command queue depth; always &ge; 0
     */
    public int totalQueuedCommands() {
        return commandQueue.size();
    }

    // ── Package-private accessors (for testing) ───────────────────────────────

    /**
     * Returns the always-active command queue (for testing and enqueue delegation).
     */
    PlayerCommandQueue commandQueue() {
        return commandQueue;
    }

    /**
     * Returns the always-active cooldown system (for clearing on death, etc.).
     */
    CooldownSystem abilityCooldowns() {
        return abilityCooldowns;
    }

    /**
     * Returns the always-active respawn ticker (for scheduling respawn on death).
     */
    PlayerRespawnTicker respawnTicker() {
        return respawnTicker;
    }

    /**
     * Returns the always-active spell cast state (for beginning/interrupting channeled casts).
     */
    SpellCastState spellCastState() {
        return spellCastState;
    }

    /**
     * Returns the always-active auto-walk state (for beginning/cancelling an auto-walk).
     */
    AutoWalkState autoWalkState() {
        return autoWalkState;
    }
}
