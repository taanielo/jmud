package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.ability.AbilityCooldownTracker;
import io.taanielo.jmud.core.ability.CooldownTracker;
import io.taanielo.jmud.core.character.repository.ClassRepositoryException;
import io.taanielo.jmud.core.character.repository.RaceRepositoryException;
import io.taanielo.jmud.core.character.repository.json.JsonClassRepository;
import io.taanielo.jmud.core.character.repository.json.JsonRaceRepository;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectMessageSink;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.effects.EffectSettings;
import io.taanielo.jmud.core.effects.PlayerEffectTicker;
import io.taanielo.jmud.core.effects.repository.json.JsonEffectRepository;
import io.taanielo.jmud.core.healing.HealingBaseResolver;
import io.taanielo.jmud.core.healing.HealingEngine;
import io.taanielo.jmud.core.healing.HealingSettings;
import io.taanielo.jmud.core.healing.PlayerHealingTicker;
import io.taanielo.jmud.core.output.OutputStyleSettings;
import io.taanielo.jmud.core.output.TextStyler;
import io.taanielo.jmud.core.output.TextStylers;
import io.taanielo.jmud.core.player.DeathSettings;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.player.PlayerRespawnTicker;
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

    private final TickRegistry tickRegistry;
    private final PlayerRepository playerRepository;
    private final RoomService roomService;

    private Player player;
    private boolean authenticated;
    private boolean connected = true;
    private TextStyler textStyler;
    private boolean quitRequested;

    private final CooldownSystem abilityCooldowns = new CooldownSystem();
    private final AbilityCooldownTracker cooldownTracker = new CooldownTracker(abilityCooldowns);
    private final PlayerRespawnTicker respawnTicker;
    private TickSubscription cooldownSubscription;
    private TickSubscription respawnSubscription;

    private final List<TickSubscription> effectSubscriptions = new ArrayList<>();
    private boolean effectsInitialized;
    private EffectMessageSink effectSink;

    private TickSubscription healingSubscription;
    private boolean healingInitialized;
    private Consumer<Player> healingCallback;

    /**
     * Creates a player session with the given dependencies.
     *
     * @param tickRegistry the tick registry for scheduling tickables
     * @param playerRepository the player repository for persistence
     * @param roomService the room service for location management
     * @param respawnCallback called when the player respawns after death
     */
    public PlayerSession(
        TickRegistry tickRegistry,
        PlayerRepository playerRepository,
        RoomService roomService,
        Consumer<Player> respawnCallback
    ) {
        this.tickRegistry = Objects.requireNonNull(tickRegistry, "Tick registry is required");
        this.playerRepository = Objects.requireNonNull(playerRepository, "Player repository is required");
        this.roomService = Objects.requireNonNull(roomService, "Room service is required");
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

    /**
     * Replaces the current player, persists the update, and re-registers
     * effect ticks if active.
     */
    public void replacePlayer(Player updated) {
        player = updated;
        playerRepository.savePlayer(player);
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
        try {
            EffectEngine engine = new EffectEngine(new JsonEffectRepository());
            effectSubscriptions.add(tickRegistry.register(new PlayerEffectTicker(player, engine, sink)));
        } catch (EffectRepositoryException e) {
            log.error("Failed to initialize effects", e);
            return;
        }
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
        this.healingCallback = callback;
        try {
            HealingEngine engine = new HealingEngine(new JsonEffectRepository());
            HealingBaseResolver baseResolver = new HealingBaseResolver(new JsonRaceRepository(), new JsonClassRepository());
            healingSubscription = tickRegistry.register(
                new PlayerHealingTicker(this::getPlayer, callback, engine, baseResolver)
            );
        } catch (EffectRepositoryException | RaceRepositoryException | ClassRepositoryException e) {
            log.error("Failed to initialize healing", e);
            return;
        }
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
     */
    public void close() {
        connected = false;
        clearEffects();
        clearHealing();
        if (cooldownSubscription != null) {
            cooldownSubscription.unsubscribe();
        }
        if (respawnSubscription != null) {
            respawnSubscription.unsubscribe();
        }
        if (authenticated && player != null) {
            playerRepository.savePlayer(player);
            log.info("Player {} data saved on disconnect.", player.getUsername());
        }
    }
}
