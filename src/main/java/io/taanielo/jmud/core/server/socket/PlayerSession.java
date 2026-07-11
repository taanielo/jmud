package io.taanielo.jmud.core.server.socket;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.ability.AbilityCooldownTracker;
import io.taanielo.jmud.core.ability.CooldownTracker;
import io.taanielo.jmud.core.dialogue.DialogueTree;
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
import io.taanielo.jmud.core.player.SustenanceSettings;
import io.taanielo.jmud.core.player.SustenanceTicker;
import io.taanielo.jmud.core.tick.TickRegistry;
import io.taanielo.jmud.core.tick.TickSubscription;
import io.taanielo.jmud.core.tick.system.CooldownSystem;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;

/**
 * Manages the lifecycle of a connected player within a single session.
 *
 * <p>Holds player state, authentication flags, and a single {@link PlayerTicker} that
 * composes all per-player tick stages (command queue, cooldowns, respawn, effects,
 * healing, resting). Exactly one {@link TickSubscription} is registered on login and
 * unregistered on logout/disconnect, eliminating the previous per-stage
 * register/unregister choreography.
 *
 * <p>I/O callbacks are provided by the caller so this class does not depend on
 * socket internals directly.
 */
@Slf4j
public class PlayerSession {

    private static final Duration DISCONNECT_FLUSH_TIMEOUT = Duration.ofSeconds(5);

    private final TickRegistry tickRegistry;
    private final PersistenceQueue persistenceQueue;
    private final EffectEngine effectEngine;
    private final EffectRepository effectRepository;
    private final HealingEngine healingEngine;
    private final HealingBaseResolver healingBaseResolver;

    private volatile Player player;
    private volatile boolean authenticated;
    private volatile boolean connected = true;
    private TextStyler textStyler;
    private boolean quitRequested;

    /**
     * Linkdead state (issue #343). When a connection drops unexpectedly the session is kept alive
     * in the world for a grace period: {@code linkdead} is {@code true}, {@code connected} is
     * {@code false}, and {@code linkdeadTicksRemaining} counts down on the tick thread until the
     * session is reaped. Both are volatile because they are written on a reader thread (at
     * disconnect) and read/decremented on the tick thread (by {@link LinkdeadTimeoutTicker}).
     */
    private volatile boolean linkdead;
    private volatile int linkdeadTicksRemaining;

    /**
     * Hook invoked (on the tick thread) when the linkdead grace period expires, so the transport
     * adapter can perform the final save, emit the {@code player.linkdead_timeout} audit event, and
     * tear the connection down. Wired by {@link SocketClient}; never touches game state itself.
     */
    private @Nullable Runnable linkdeadExpiryHandler;

    private final CooldownSystem abilityCooldowns = new CooldownSystem();
    private final AbilityCooldownTracker cooldownTracker = new CooldownTracker(abilityCooldowns);
    private final PlayerCommandQueue commandQueue = new PlayerCommandQueue();
    private final PlayerRespawnTicker respawnTicker;

    /** Single composed ticker — one subscription per player session. */
    private final PlayerTicker playerTicker;
    private TickSubscription playerTickerSubscription;

    /**
     * Tracks the effect sink so that {@link #replacePlayer} can re-enable effects
     * on the new player instance after a player-state replacement.
     */
    private EffectMessageSink effectSink;

    /** Optional hook invoked (on the tick thread) whenever a player save fails, after logging. */
    private Consumer<Player> saveFailureHandler;

    /**
     * Active NPC conversation state (see the {@code TALK}/{@code RESPOND} commands), or {@code null}
     * when the player is not talking to anyone. Held here — not on the persisted player — because a
     * conversation is transient session state that is cleared on room change, movement, or logout.
     */
    private @Nullable ActiveDialogue activeDialogue;

    /**
     * Transient "away from keyboard" state (issue #464). Turned on by the {@code AFK} command and
     * cleared automatically by the player's next command; a non-null {@code awayMessage} holds an
     * optional custom reason. This state is per-session only — it is never written to the persisted
     * {@link Player} record, so there is no save-schema change, and it is dropped on logout and on
     * reconnect. Both fields are read and written on the tick thread (AFK command execution, the
     * next-command auto-clear, and WHO/TELL rendering all run there), so they need no synchronization
     * (AGENTS.md §5).
     */
    private boolean away;
    private @Nullable String awayMessage;

    /**
     * A player's current position within an NPC dialogue tree.
     *
     * @param tree     the dialogue tree being traversed
     * @param nodeId   the id of the node the player is currently at
     * @param roomId   the room in which the conversation started; leaving it ends the conversation
     * @param speaker  the display name of the NPC being spoken to
     */
    private record ActiveDialogue(DialogueTree tree, String nodeId, RoomId roomId, String speaker) {
    }

    /**
     * Creates a player session with the given dependencies.
     *
     * @param tickRegistry the tick registry for scheduling tickables
     * @param persistenceQueue the write-behind queue used for all player saves, so
     *                         this session never blocks the tick thread on disk I/O
     * @param roomService the room service for location management
     * @param respawnCallback called when the player respawns after death
     * @param effectEngine effect engine for player effects
     * @param effectRepository repository for effect definitions
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
        Objects.requireNonNull(roomService, "Room service is required");
        this.effectEngine = Objects.requireNonNull(effectEngine, "Effect engine is required");
        this.effectRepository = Objects.requireNonNull(effectRepository, "Effect repository is required");
        this.healingEngine = Objects.requireNonNull(healingEngine, "Healing engine is required");
        this.healingBaseResolver = Objects.requireNonNull(healingBaseResolver, "Healing base resolver is required");
        this.respawnTicker = new PlayerRespawnTicker(
            this::getPlayer, respawnCallback, roomService, DeathSettings.respawnTicks()
        );
        this.playerTicker = new PlayerTicker(commandQueue, abilityCooldowns, respawnTicker);
        this.textStyler = TextStylers.forEnabled(OutputStyleSettings.ansiEnabledByDefault());
    }

    /**
     * Registers the single composed {@link PlayerTicker} subscription.
     * Must be called once after construction, before the player enters the game.
     */
    public void startTicks() {
        playerTickerSubscription = tickRegistry.register(playerTicker);
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

    /**
     * Begins an NPC conversation, replacing any conversation already in progress.
     *
     * @param tree    the dialogue tree being entered
     * @param nodeId  the starting node id
     * @param roomId  the room the conversation takes place in
     * @param speaker the NPC's display name
     */
    public void startDialogue(DialogueTree tree, String nodeId, RoomId roomId, String speaker) {
        this.activeDialogue = new ActiveDialogue(
            Objects.requireNonNull(tree, "Dialogue tree is required"),
            Objects.requireNonNull(nodeId, "Node id is required"),
            Objects.requireNonNull(roomId, "Room id is required"),
            Objects.requireNonNull(speaker, "Speaker is required"));
    }

    /**
     * Returns whether the player is currently in an NPC conversation.
     */
    public boolean isInDialogue() {
        return activeDialogue != null;
    }

    /**
     * Returns the active dialogue tree, or {@code null} when no conversation is in progress.
     */
    public @Nullable DialogueTree getDialogueTree() {
        return activeDialogue == null ? null : activeDialogue.tree();
    }

    /**
     * Returns the current dialogue node id, or {@code null} when no conversation is in progress.
     */
    public @Nullable String getDialogueNodeId() {
        return activeDialogue == null ? null : activeDialogue.nodeId();
    }

    /**
     * Returns the room the active conversation started in, or {@code null} when none is in progress.
     */
    public @Nullable RoomId getDialogueRoomId() {
        return activeDialogue == null ? null : activeDialogue.roomId();
    }

    /**
     * Returns the NPC display name for the active conversation, or {@code null} when none is in
     * progress.
     */
    public @Nullable String getDialogueSpeaker() {
        return activeDialogue == null ? null : activeDialogue.speaker();
    }

    /**
     * Advances the active conversation to a new node, keeping the same tree, room, and speaker.
     * No-op when no conversation is in progress.
     *
     * @param nodeId the node id to advance to
     */
    public void advanceDialogueNode(String nodeId) {
        if (activeDialogue == null) {
            return;
        }
        this.activeDialogue = new ActiveDialogue(
            activeDialogue.tree(),
            Objects.requireNonNull(nodeId, "Node id is required"),
            activeDialogue.roomId(),
            activeDialogue.speaker());
    }

    /**
     * Ends any active NPC conversation.
     */
    public void clearDialogue() {
        this.activeDialogue = null;
    }

    // ── AFK / away status (issue #464) ──────────────────────────────────

    /**
     * Marks this session away from keyboard, optionally with a custom reason. A blank or {@code null}
     * message clears any previous custom reason, leaving the session away with the default message.
     *
     * @param message the custom away reason, or {@code null}/blank for the default
     */
    public void setAway(@Nullable String message) {
        this.away = true;
        this.awayMessage = (message == null || message.isBlank()) ? null : message.trim();
    }

    /**
     * Clears any away status, so the player is no longer marked AFK.
     */
    public void clearAway() {
        this.away = false;
        this.awayMessage = null;
    }

    /**
     * Returns whether this session is currently marked away from keyboard.
     *
     * @return {@code true} while the player is AFK
     */
    public boolean isAway() {
        return away;
    }

    /**
     * Returns the custom away reason, or {@code null} when the player is not away or set no custom
     * message.
     *
     * @return the away message, or {@code null}
     */
    public @Nullable String awayMessage() {
        return awayMessage;
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
     * Returns the composed per-player ticker (primarily for testing or introspection).
     */
    public PlayerTicker getPlayerTicker() {
        return playerTicker;
    }

    /**
     * Attempts to enqueue a command for execution on the tick thread. Safe to call
     * from reader threads; never blocks.
     *
     * @param command the command to run on the tick thread
     * @return {@code true} if the command was accepted; {@code false} if the
     *     bounded queue is full and the command was dropped — the caller should
     *     inform the player without touching game state (AGENTS.md §5)
     */
    public boolean enqueueCommand(Runnable command) {
        return commandQueue.enqueue(command);
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
        if (playerTicker.isEffectsEnabled()) {
            playerTicker.disableEffects();
            if (!player.isDead()) {
                registerEffects(effectSink);
            }
        }
        handleDeathState();
    }

    /**
     * Enables the effect-tick stage for the current player inside the composed
     * {@link PlayerTicker}. No-op when effects are globally disabled, already active,
     * or when the player is absent or dead.
     *
     * @param sink the message sink for effect tick messages
     */
    public void registerEffects(EffectMessageSink sink) {
        if (!EffectSettings.enabled() || playerTicker.isEffectsEnabled() || player == null || player.isDead()) {
            return;
        }
        this.effectSink = sink;
        playerTicker.enableEffects(new PlayerEffectTicker(player, effectEngine, sink));
    }

    /**
     * Disables the effect-tick stage inside the composed {@link PlayerTicker}.
     */
    public void clearEffects() {
        playerTicker.disableEffects();
    }

    /**
     * Enables the healing-tick stage inside the composed {@link PlayerTicker}.
     * No-op when healing is globally disabled, already active, or when the
     * player is absent or dead (mirrors {@link #registerEffects(EffectMessageSink)});
     * this avoids constructing a {@link PlayerHealingTicker} with a null effect sink,
     * which would otherwise throw on a dead-player re-login.
     *
     * @param callback called when healing produces an updated player
     */
    public void registerHealing(Consumer<Player> callback) {
        if (!HealingSettings.enabled() || playerTicker.isHealingEnabled() || player == null || player.isDead()) {
            return;
        }
        playerTicker.enableHealing(
            new PlayerHealingTicker(
                this::getPlayer,
                callback,
                healingEngine,
                healingBaseResolver,
                effectRepository,
                effectSink
            )
        );
    }

    /**
     * Disables the healing-tick stage inside the composed {@link PlayerTicker}.
     */
    public void clearHealing() {
        playerTicker.disableHealing();
    }

    /**
     * Enables the resting-tick stage inside the composed {@link PlayerTicker}.
     * Replaces any existing resting ticker. Typically called by the REST command.
     *
     * @param ticker the resting ticker to activate
     */
    public void registerRestingTicker(RestingTicker ticker) {
        playerTicker.enableResting(ticker);
    }

    /**
     * Enables the sustenance-decay stage inside the composed {@link PlayerTicker}.
     * No-op when sustenance is globally disabled or already active.
     *
     * @param callback    called on the tick thread with the player after each decay
     * @param warningSink called with a warning line when hunger/thirst newly cross the penalty threshold
     */
    public void registerSustenance(Consumer<Player> callback, Consumer<String> warningSink) {
        if (!SustenanceSettings.enabled() || playerTicker.isSustenanceEnabled()) {
            return;
        }
        playerTicker.enableSustenance(
            new SustenanceTicker(
                this::getPlayer,
                callback,
                warningSink,
                SustenanceSettings.decayPerTick()
            )
        );
    }

    /**
     * Disables the sustenance-decay stage inside the composed {@link PlayerTicker}.
     */
    public void clearSustenance() {
        playerTicker.disableSustenance();
    }

    /**
     * Disables the resting-tick stage inside the composed {@link PlayerTicker}.
     * Typically called by the WAKE command or when a mob interrupts rest.
     */
    public void clearRestingTicker() {
        playerTicker.disableResting();
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

    // ── Linkdead lifecycle (issue #343) ─────────────────────────────────

    /**
     * Registers the hook invoked on the tick thread when this session's linkdead grace period
     * expires. The transport adapter uses it to save the player one final time, emit the
     * {@code player.linkdead_timeout} audit event, and tear down the connection.
     *
     * @param linkdeadExpiryHandler the expiry callback; may be null to clear
     */
    public void setLinkdeadExpiryHandler(@Nullable Runnable linkdeadExpiryHandler) {
        this.linkdeadExpiryHandler = linkdeadExpiryHandler;
    }

    /**
     * Transitions this session to the linkdead state after an unexpected disconnect. The composed
     * ticker stays subscribed and the player remains in their room; only the {@code connected} flag
     * is cleared and the countdown started, so the world can continue to see, tick, and target the
     * player while a reconnecting client has a chance to reattach.
     *
     * @param ticks the number of ticks to linger before the session is reaped; clamped to at least 1
     */
    public void startLinkdead(int ticks) {
        this.connected = false;
        this.linkdead = true;
        this.linkdeadTicksRemaining = Math.max(1, ticks);
    }

    /**
     * Returns whether this session is currently linkdead (dropped connection, awaiting reattach or
     * timeout).
     *
     * @return {@code true} while the session is linkdead
     */
    public boolean isLinkdead() {
        return linkdead;
    }

    /**
     * Returns the number of ticks remaining before a linkdead session is reaped (primarily for
     * tests and introspection).
     *
     * @return the remaining linkdead countdown, or {@code 0} when not linkdead
     */
    public int linkdeadTicksRemaining() {
        return linkdeadTicksRemaining;
    }

    /**
     * Decrements the linkdead countdown by one tick. Must be called on the tick thread.
     *
     * @return {@code true} when the countdown has reached zero and the session should be reaped;
     *     {@code false} otherwise (including when the session is not linkdead)
     */
    // Single-writer: tickLinkdead() runs only on the tick thread (AGENTS.md §5), so the
    // volatile read-decrement-write is not a multi-writer race.
    @SuppressWarnings("NonAtomicVolatileUpdate")
    public boolean tickLinkdead() {
        if (!linkdead) {
            return false;
        }
        linkdeadTicksRemaining--;
        return linkdeadTicksRemaining <= 0;
    }

    /**
     * Clears linkdead state when a live transport takes over the session again. Marks the session
     * connected, resets the countdown, and drops any transient NPC conversation (dialogue is not
     * carried across a reconnect). Does <em>not</em> reload the player from disk or re-subscribe
     * ticks — the tick subscription is never dropped while linkdead.
     */
    public void reattach() {
        this.connected = true;
        this.linkdead = false;
        this.linkdeadTicksRemaining = 0;
        clearDialogue();
        clearAway();
    }

    /**
     * Invoked by {@link LinkdeadTimeoutTicker} when the grace period expires. Clears linkdead state
     * and runs the registered expiry hook (final save, audit, transport teardown), if any.
     */
    public void expireLinkdead() {
        this.linkdead = false;
        this.linkdeadTicksRemaining = 0;
        if (linkdeadExpiryHandler != null) {
            linkdeadExpiryHandler.run();
        }
    }

    /**
     * Unsubscribes the composed ticker without saving or flushing. Used when a reconnecting client
     * adopts this session's live player, so the old session stops ticking without disturbing the
     * player's persisted state (the new session now owns the save path).
     */
    public void unsubscribeTicks() {
        if (playerTickerSubscription != null) {
            playerTickerSubscription.unsubscribe();
        }
    }

    /**
     * Closes the session by unsubscribing the single composed ticker and persisting
     * the player.
     *
     * <p>Unlike normal in-play saves, disconnect has no further chance to persist,
     * so this enqueues the final save and then synchronously {@link
     * PersistenceQueue#flush(Duration) flushes} the queue (bounded by {@link
     * #DISCONNECT_FLUSH_TIMEOUT}) before returning, guaranteeing the write has
     * completed (or definitively failed) by the time the connection is torn down.
     */
    public void close() {
        connected = false;
        if (playerTickerSubscription != null) {
            playerTickerSubscription.unsubscribe();
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
