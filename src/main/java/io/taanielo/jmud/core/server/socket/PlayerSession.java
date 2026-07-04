package io.taanielo.jmud.core.server.socket;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.ability.AbilityCooldownTracker;
import io.taanielo.jmud.core.ability.CooldownTracker;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectMessageSink;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectSettings;
import io.taanielo.jmud.core.effects.PlayerEffectTicker;
import io.taanielo.jmud.core.healing.HealingBaseResolver;
import io.taanielo.jmud.core.healing.HealingEngine;
import io.taanielo.jmud.core.healing.HealingSettings;
import io.taanielo.jmud.core.healing.PlayerHealingTicker;
import io.taanielo.jmud.core.output.OutputStyleSettings;
import io.taanielo.jmud.core.output.TextStyler;
import io.taanielo.jmud.core.output.TextStylers;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.DeathSettings;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRespawnTicker;
import io.taanielo.jmud.core.player.RestingTicker;
import io.taanielo.jmud.core.tick.TickRegistry;
import io.taanielo.jmud.core.tick.TickSubscription;
import io.taanielo.jmud.core.tick.system.CooldownSystem;
import io.taanielo.jmud.core.world.RoomService;

/**
 * Manages the lifecycle of a connected player within a single session.
 *
 * <p>Holds player state, authentication flags, tick subscriptions for effects,
 * healing, cooldowns, and respawn. I/O callbacks are provided by the caller
 * so this class does not depend on socket internals directly.
 */
@Slf4j
public class PlayerSession {

    private static final Duration DISCONNECT_FLUSH_TIMEOUT = Duration.ofSeconds(5);

    private final TickRegistry tickRegistry;
    private final PersistenceQueue persistenceQueue;
    private final RoomService roomService;
    private final PlayerCommandQueue commandQueue = new PlayerCommandQueue();
    private final EffectEngine effectEngine;
    private final EffectRepository effectRepository;
    private final HealingEngine healingEngine;
    private final HealingBaseResolver healingBaseResolver;

    private volatile Player player;
    private volatile boolean authenticated;
    private boolean connected = true;
    private TextStyler textStyler;
    private boolean quitRequested;

    private final CooldownSystem abilityCooldowns = new CooldownSystem();
    private final AbilityCooldownTracker cooldownTracker = new CooldownTracker(abilityCooldowns);
    private final PlayerRespawnTicker respawnTicker;
    private TickSubscription cooldownSubscription;
    private TickSubscription respawnSubscription;
    private TickSubscription commandSubscription;

    private final List<TickSubscription> effectSubscriptions = new ArrayList<>();
    private boolean effectsInitialized;
    private EffectMessageSink effectSink;

    private TickSubscription healingSubscription;
    private boolean healingInitialized;

    private TickSubscription restingSubscription;

    /** Optional hook invoked (on the tick thread) whenever a player save fails, after logging. */
    private Consumer<Player> saveFailureHandler;

    /**
     * Creates a player session with the given dependencies.
     *
     * @param tickRegistry the tick registry for scheduling tickables
     * @param persistenceQueue the write-behind queue used for all player saves, so
     *                         this session never blocks the tick thread on disk I/O
     * @param roomService the room service for location management
     * @param respawnCallback called when the player respawns after death
     * @param effectEngine effect engine for player effects
     * @param healingEngine healing engine for player recovery
     * @param healingBaseResolver base resolver for healing calculations
     */
    public PlayerSession(
        TickRegistry tickRegistry,
        PersistenceQueue persistenceQueue,
        RoomService roomService,
        Consumer<Player> respawnCallback,
        EffectEngine effectEngine,
        EffectRepository effectRepository,
        HealingEngine healingEngine,
        HealingBaseResolver healingBaseResolver
    ) {
        this.tickRegistry = Objects.requireNonNull(tickRegistry, "Tick registry is required");
        this.persistenceQueue = Objects.requireNonNull(persistenceQueue, "Persistence queue is required");
        this.roomService = Objects.requireNonNull(roomService, "Room service is required");
        this.effectEngine = Objects.requireNonNull(effectEngine, "Effect engine is required");
        this.effectRepository = Objects.requireNonNull(effectRepository, "Effect repository is required");
        this.healingEngine = Objects.requireNonNull(healingEngine, "Healing engine is required");
        this.healingBaseResolver = Objects.requireNonNull(healingBaseResolver, "Healing base resolver is required");
        this.respawnTicker = new PlayerRespawnTicker(
            this::getPlayer, respawnCallback, roomService, DeathSettings.respawnTicks()
        );
        this.textStyler = TextStylers.forEnabled(OutputStyleSettings.ansiEnabledByDefault());
    }

    /**
     * Registers global tick subscriptions (cooldowns and respawn).
     * Must be called once after construction.
     */
    public void startTicks() {
        commandSubscription = tickRegistry.register(commandQueue);
        cooldownSubscription = tickRegistry.register(abilityCooldowns);
        respawnSubscription = tickRegistry.register(respawnTicker);
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public TextStyler getTextStyler() {
        return textStyler;
    }

    public void setTextStyler(TextStyler textStyler) {
        this.textStyler = textStyler;
    }

    public boolean isQuitRequested() {
        return quitRequested;
    }

    public void setQuitRequested(boolean quitRequested) {
        this.quitRequested = quitRequested;
    }

    public AbilityCooldownTracker getCooldownTracker() {
        return cooldownTracker;
    }

    public CooldownSystem getAbilityCooldowns() {
        return abilityCooldowns;
    }

    public PlayerRespawnTicker getRespawnTicker() {
        return respawnTicker;
    }

    public void enqueueCommand(Runnable command) {
        commandQueue.enqueue(command);
    }

    /**
     * Registers a hook invoked whenever a player save fails, after the failure has
     * already been logged. Used by the transport layer to warn the player and emit
     * an audit event without embedding transport concerns in this class.
     *
     * @param saveFailureHandler callback receiving the player whose save failed; may be null
     */
    public void setSaveFailureHandler(Consumer<Player> saveFailureHandler) {
        this.saveFailureHandler = saveFailureHandler;
    }

    /**
     * Hands the given player off to the write-behind persistence queue rather than
     * saving synchronously (AGENTS.md §5); the queue itself logs and audits any
     * eventual save failure after its retry.
     *
     * @param playerToSave the player to save
     */
    private void enqueueSave(Player playerToSave) {
        persistenceQueue.enqueueSave(playerToSave);
    }

    /**
     * Replaces the current player, persists the update, and re-registers
     * effect ticks if active.
     */
    public void replacePlayer(Player updated) {
        player = updated;
        enqueueSave(player);
        if (effectsInitialized) {
            clearEffects();
            if (!player.isDead()) {
                registerEffects(effectSink);
            }
        }
        handleDeathState();
    }

    /**
     * Registers effect tick subscriptions for the current player.
     *
     * @param sink the message sink for effect tick messages
     */
    public void registerEffects(EffectMessageSink sink) {
        if (!EffectSettings.enabled() || effectsInitialized || player == null || player.isDead()) {
            return;
        }
        this.effectSink = sink;
        effectSubscriptions.add(tickRegistry.register(new PlayerEffectTicker(player, effectEngine, sink)));
        effectsInitialized = true;
    }

    /**
     * Unsubscribes all effect tick subscriptions.
     */
    public void clearEffects() {
        for (TickSubscription subscription : effectSubscriptions) {
            subscription.unsubscribe();
        }
        effectSubscriptions.clear();
        effectsInitialized = false;
    }

    /**
     * Registers healing tick subscriptions for the current player.
     *
     * @param callback called when healing produces an updated player
     */
    public void registerHealing(Consumer<Player> callback) {
        if (!HealingSettings.enabled() || healingInitialized) {
            return;
        }
        healingSubscription = tickRegistry.register(
            new PlayerHealingTicker(
                this::getPlayer,
                callback,
                healingEngine,
                healingBaseResolver,
                effectRepository,
                effectSink
            )
        );
        healingInitialized = true;
    }

    /**
     * Unsubscribes the healing tick subscription.
     */
    public void clearHealing() {
        if (healingSubscription != null) {
            healingSubscription.unsubscribe();
            healingSubscription = null;
        }
        healingInitialized = false;
    }

    /**
     * Registers a resting ticker for the current session.
     *
     * @param ticker the resting ticker to schedule
     */
    public void registerRestingTicker(RestingTicker ticker) {
        clearRestingTicker();
        restingSubscription = tickRegistry.register(ticker);
    }

    /**
     * Unsubscribes the resting tick subscription, if any.
     */
    public void clearRestingTicker() {
        if (restingSubscription != null) {
            restingSubscription.unsubscribe();
            restingSubscription = null;
        }
    }

    /**
     * Schedules respawn if the player is dead and not already scheduled.
     */
    public void handleDeathState() {
        if (player == null || !player.isDead()) {
            return;
        }
        if (respawnTicker.isScheduled()) {
            return;
        }
        abilityCooldowns.clear();
        respawnTicker.schedule();
    }

    /**
     * Closes the session by unsubscribing all ticks and persisting the player.
     *
     * <p>Unlike normal in-play saves, disconnect has no further chance to persist,
     * so this enqueues the final save and then synchronously {@link
     * PersistenceQueue#flush(Duration) flushes} the queue (bounded by {@link
     * #DISCONNECT_FLUSH_TIMEOUT}) before returning, guaranteeing the write has
     * completed (or definitively failed) by the time the connection is torn down.
     */
    public void close() {
        connected = false;
        clearEffects();
        clearHealing();
        clearRestingTicker();
        if (cooldownSubscription != null) {
            cooldownSubscription.unsubscribe();
        }
        if (respawnSubscription != null) {
            respawnSubscription.unsubscribe();
        }
        if (commandSubscription != null) {
            commandSubscription.unsubscribe();
        }
        if (authenticated && player != null) {
            long failuresBeforeSave = persistenceQueue.getFailureCount();
            enqueueSave(player);
            boolean flushed = persistenceQueue.flush(DISCONNECT_FLUSH_TIMEOUT);
            boolean saveFailed = persistenceQueue.getFailureCount() > failuresBeforeSave;
            if (flushed && !saveFailed) {
                log.info("Player {} data saved on disconnect.", player.getUsername());
            } else {
                log.error("Failed to save player {} on disconnect (flushed={}, failureDetected={}).",
                    player.getUsername(), flushed, saveFailed);
                if (saveFailureHandler != null) {
                    saveFailureHandler.accept(player);
                }
            }
        }
    }
}
