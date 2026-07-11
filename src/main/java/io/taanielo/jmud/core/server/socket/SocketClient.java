package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.net.SocketException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.bootstrap.GameContext;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.AuditSubject;
import io.taanielo.jmud.core.authentication.AuthenticationService;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.creation.CharacterCreationException;
import io.taanielo.jmud.core.creation.CharacterCreationService;
import io.taanielo.jmud.core.creation.CharacterCreationState;
import io.taanielo.jmud.core.creation.CharacterCreationState.ChoosingClass;
import io.taanielo.jmud.core.creation.CharacterCreationState.ChoosingRace;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.UserSayMessage;
import io.taanielo.jmud.core.messaging.WelcomeBannerMessage;
import io.taanielo.jmud.core.messaging.WelcomeMessage;
import io.taanielo.jmud.core.output.OutputStyleSettings;
import io.taanielo.jmud.core.output.TextStylers;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.prompt.PromptSettings;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.connection.ClientConnection;
import io.taanielo.jmud.core.server.connection.TransportSecurity;
import io.taanielo.jmud.core.world.Direction;

/**
 * Thin transport adapter: read a line → enqueue → render results (AGENTS.md §3.3, issue #182).
 * All game logic lives in {@link SocketCommandContextImpl}; session state in {@link PlayerSession}.
 * The ArchUnit rule {@code socket_client_no_game_action_dependency} prevents regression.
 */
@Slf4j
public class SocketClient implements Client {

    private final ClientConnection connection;
    private final GameContext context;
    private final PlayerSession session;
    private final AuthenticationService authenticationService;
    private final ClientPool clientPool;
    private final AuditService auditService;
    private final TransportSecurity transportSecurity;
    private final SocketCommandDispatcher commandDispatcher;
    private SocketCommandContextImpl commandContext;
    private final User preAuthenticatedUser;
    private final boolean preAuthenticatedNewUser;
    private final Runnable onClose;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile CharacterCreationState creationState;
    private final PlayerSessionRegistry sessionRegistry;

    /** Set (on the tick thread) when this session is being torn down due to a linkdead timeout, so
     * {@link #fullClose()} emits {@code player.linkdead_timeout} instead of {@code player.logout}. */
    private volatile boolean linkdeadExpired;

    public SocketClient(
        ClientConnection connection,
        AuthenticationService authenticationService,
        GameContext context,
        ClientPool clientPool,
        User preAuthenticatedUser,
        boolean preAuthenticatedNewUser,
        Runnable onClose,
        TransportSecurity transportSecurity
    ) {
        this.context = Objects.requireNonNull(context, "Game context is required");
        this.connection = Objects.requireNonNull(connection, "Connection is required");
        this.authenticationService = Objects.requireNonNull(authenticationService, "Authentication service is required");
        this.clientPool = clientPool;
        this.auditService = Objects.requireNonNull(context.auditService(), "Audit service is required");
        this.transportSecurity = Objects.requireNonNull(transportSecurity, "Transport security is required");
        this.preAuthenticatedUser = preAuthenticatedUser;
        this.preAuthenticatedNewUser = preAuthenticatedNewUser;
        this.onClose = onClose;
        this.session = new PlayerSession(
            context.tickRegistry(), context.persistenceQueue(), context.roomService(),
            p -> commandContext.applyRespawnUpdate(p),
            context.effectEngine(), context.effectRepository(),
            context.healingEngine(), context.healingBaseResolver()
        );
        this.commandDispatcher = new SocketCommandDispatcher(context.commandRegistry(), auditService);
        this.commandContext = new SocketCommandContextImpl(
            this, connection, session, context, clientPool, commandDispatcher
        );
        this.session.setSaveFailureHandler(commandContext::handleSaveFailure);
        this.sessionRegistry = context.playerSessionRegistry();
        this.session.setLinkdeadExpiryHandler(this::handleLinkdeadTimeout);
    }

    @Override
    public void run() {
        log.debug("Initializing connection ..");
        try {
            connection.open();
            session.setConnected(true);
        } catch (IOException e) {
            log.error("Error connecting client", e);
        }
        if (transportSecurity == TransportSecurity.INSECURE) {
            connection.writeLine("Warning: Telnet is unencrypted. Use SSH for secure access.");
        }
        session.startTicks();
        int onlineCount = Math.max(0, clientPool.clients().size() - 1);
        if (preAuthenticatedUser != null) {
            sendMessage(WelcomeBannerMessage.of(session.getTextStyler(), onlineCount));
            completeAuthentication(preAuthenticatedUser, preAuthenticatedNewUser);
        } else {
            sendMessage(WelcomeMessage.of(session.getTextStyler(), onlineCount));
            session.setAuthenticated(false);
        }
        try {
            String clientInput;
            while (session.isConnected() && (clientInput = connection.readLine()) != null) {
                log.debug("Received: \"{}\"", clientInput);
                if (!session.isAuthenticated()) {
                    handleAuthentication(clientInput);
                } else if (creationState != null) {
                    handleCharacterCreation(clientInput);
                } else {
                    handleCommand(clientInput);
                }
            }
        } catch (IOException e) {
            if (e instanceof SocketException && (!session.isConnected() || session.isQuitRequested())) {
                log.debug("Client socket closed");
            } else {
                log.error("Error receiving", e);
            }
        }
        log.info("Client disconnected");
        close();
    }

    @Override
    public void sendMessage(Message message) {
        try {
            connection.sendMessage(message);
        } catch (IOException e) {
            log.error("Error sending message", e);
        }
        commandContext.sendPrompt();
    }
    public void sendMessage(UserSayMessage message) {
        Player player = session.getPlayer();
        if (player != null && message.getUsername().equals(player.getUsername())) {
            return;
        }
        try {
            connection.sendMessage(message);
        } catch (IOException e) {
            log.error("Error sending message", e);
        }
        commandContext.sendPrompt();
    }
    @Override
    public Optional<Player> currentPlayer() {
        return session.isAuthenticated() ? Optional.ofNullable(session.getPlayer()) : Optional.empty();
    }

    @Override
    public boolean isInWorld() {
        return creationState == null && currentPlayer().isPresent();
    }

    @Override
    public void close() {
        if (shouldGoLinkdead()) {
            int ticks = LinkdeadSettings.timeoutTicks();
            session.startLinkdead(ticks);
            log.info("Player {} went linkdead; holding session for {} ticks.",
                session.getPlayer().getUsername(), ticks);
            // Close the dropped socket transport, but keep the client in the pool and the composed
            // ticker subscribed so the player stays visible, tickable, and reattachable (issue #343).
            connection.close();
            return;
        }
        fullClose();
    }

    /**
     * Decides whether a disconnecting client should linger as a linkdead session (issue #343)
     * instead of tearing down immediately. Only in-world, authenticated players whose connection
     * dropped unexpectedly (not an explicit QUIT) qualify.
     */
    private boolean shouldGoLinkdead() {
        return LinkdeadSettings.enabled()
            && sessionRegistry != null
            && session.isAuthenticated()
            && session.getPlayer() != null
            && creationState == null
            && !session.isQuitRequested()
            && !session.isLinkdead();
    }

    /**
     * Invoked (on the tick thread) by the session's linkdead expiry hook when the grace period runs
     * out: performs the final save and full transport teardown, emitting {@code player.linkdead_timeout}.
     */
    private void handleLinkdeadTimeout() {
        linkdeadExpired = true;
        fullClose();
    }

    /**
     * Reclaims this (dropped) client's transport when a reconnecting login has adopted its live
     * session: removes it from the pool and closes the socket without saving, unsubscribing, or
     * emitting a logout — the new session now owns the player and its save path (issue #343).
     */
    void detachForReattach() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        connection.close();
        clientPool.remove(this);
        if (onClose != null) {
            onClose.run();
        }
    }

    private void fullClose() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        log.debug("Closing connection ..");
        Player player = session.getPlayer();
        if (session.isAuthenticated() && player != null) {
            String eventType = linkdeadExpired ? "player.linkdead_timeout" : "player.logout";
            String reason = linkdeadExpired
                ? "linkdead_timeout"
                : (session.isQuitRequested() ? "quit" : "disconnect");
            commandContext.emitAudit(eventType, AuditSubject.player(player.getUsername()),
                null, commandContext.resolveRoomId(player), "success",
                Map.of("reason", reason));
        }
        if (sessionRegistry != null && player != null) {
            sessionRegistry.removeIf(player.getUsername(), session);
        }
        if (context.playerEventBus() != null && session.getPlayer() != null) {
            context.playerEventBus().unregister(session.getPlayer().getUsername());
        }
        if (context.duelService() != null && session.getPlayer() != null) {
            // Clear any pending/active duel so a disconnect never leaves the opponent stuck.
            context.duelService().clearFor(session.getPlayer().getUsername());
        }
        if (context.partyService() != null && session.getPlayer() != null) {
            // Clear any auto-follow relationship so a disconnect never leaves a follower stuck.
            context.partyService().clearFollowsInvolving(session.getPlayer().getUsername());
        }
        session.close();
        connection.close();
        clientPool.remove(this);
        if (onClose != null) {
            onClose.run();
        }
        log.debug("Connection closed");
    }

    private void handleAuthentication(String clientInput) throws IOException {
        authenticationService.authenticate(clientInput, u -> completeAuthentication(u, false));
    }
    // Identity comparison below (existing != session) is intentional: PlayerSession instances are
    // unique per connection with no value equality, so identity correctly distinguishes a different
    // linkdead session from this connection's own session.
    @SuppressWarnings("ReferenceEquality")
    private void completeAuthentication(User authenticatedUser, boolean isNewPlayer) {
        session.setAuthenticated(true);
        Username username = authenticatedUser.getUsername();
        PlayerSession existing = sessionRegistry == null
            ? null
            : sessionRegistry.lookup(username).orElse(null);
        if (existing != null && existing != session && existing.isLinkdead()
            && existing.getPlayer() != null) {
            reattachToLinkdeadSession(existing, username);
            return;
        }
        AtomicBoolean created = new AtomicBoolean(isNewPlayer);
        Player player = context.playerRepository()
            .loadPlayer(username)
            .orElseGet(() -> {
                Player newPlayer = Player.of(authenticatedUser, PromptSettings.defaultFormat(),
                    OutputStyleSettings.ansiEnabledByDefault(), List.of());
                created.set(true);
                context.persistenceQueue().enqueueSave(newPlayer);
                return newPlayer;
            });
        session.setPlayer(player);
        session.setTextStyler(TextStylers.forEnabled(player.isAnsiEnabled()));
        if (player.isDead()) {
            context.roomService().clearPlayerLocation(player.getUsername());
        } else {
            context.roomService().ensurePlayerLocation(player.getUsername());
        }
        if (sessionRegistry != null) {
            sessionRegistry.register(username, session);
        }
        commandContext.emitAudit("player.login", AuditSubject.player(player.getUsername()),
            null, commandContext.resolveRoomId(player), "success", Map.of("newPlayer", created.get()));
        commandContext.registerPostLoginCallbacks(created.get());
    }

    /**
     * Reattaches this fresh connection to an existing linkdead session (issue #343). The dropped
     * client's live in-memory player is adopted verbatim — no disk reload — so the reconnecting
     * player resumes exactly where they were (room, HP, inventory, buffs). The stale session's
     * ticker is unsubscribed and its client removed from the pool, leaving this connection as the
     * sole owner of the player, then post-login wiring re-establishes effect/healing ticks on this
     * session and shows the current room.
     *
     * <p>Runs on this connection's reader thread. Because the single tick thread never runs two
     * tickables concurrently, unsubscribing the old ticker (a thread-safe list removal that takes
     * effect on the next tick) plus reading the old session's volatile player reference is safe; at
     * worst one final healing/effect tick lands on the old player just after we snapshot it, a
     * benign one-tick staleness.
     *
     * @param existing the linkdead session being reclaimed
     * @param username the reconnecting player's username
     */
    private void reattachToLinkdeadSession(PlayerSession existing, Username username) {
        Player livePlayer = existing.getPlayer();
        existing.reattach();
        existing.unsubscribeTicks();
        detachOldClient(existing);
        session.setPlayer(livePlayer);
        session.setTextStyler(TextStylers.forEnabled(livePlayer.isAnsiEnabled()));
        if (livePlayer.isDead()) {
            context.roomService().clearPlayerLocation(username);
        } else {
            context.roomService().ensurePlayerLocation(username);
        }
        if (sessionRegistry != null) {
            sessionRegistry.register(username, session);
        }
        commandContext.emitAudit("player.reattach", AuditSubject.player(username),
            null, commandContext.resolveRoomId(livePlayer), "success", Map.of());
        log.info("Player {} reattached to linkdead session.", username.getValue());
        commandContext.registerPostLoginCallbacks(false);
    }

    /**
     * Removes and closes the dropped {@link SocketClient} that still owns the linkdead session being
     * reattached, so exactly one client and one ticker remain for the username.
     */
    // Identity comparisons below (sc != this, sc.session() == existing) are intentional: SocketClient
    // and PlayerSession instances are unique per connection with no value equality, so identity is the
    // correct way to find the other client that still owns the exact linkdead session.
    @SuppressWarnings("ReferenceEquality")
    private void detachOldClient(PlayerSession existing) {
        for (Client c : clientPool.clients()) {
            if (c instanceof SocketClient sc && sc != this && sc.session() == existing) {
                sc.detachForReattach();
                return;
            }
        }
    }
    void beginCharacterCreation() {
        creationState = new ChoosingRace();
        CharacterCreationService svc = context.characterCreationService();
        try {
            connection.writeLine(svc.buildRacePrompt());
        } catch (CharacterCreationException e) {
            log.error("Failed to build race prompt", e);
            connection.writeLine("Character creation unavailable. You have been assigned a default race and class.");
            finishCharacterCreation(null, null);
        }
    }
    private void handleCharacterCreation(String input) {
        CharacterCreationService svc = context.characterCreationService();
        if (svc == null) { creationState = null; return; }
        try {
            switch (creationState) {
                case ChoosingRace ignored -> {
                    var opt = svc.resolveRace(input);
                    if (opt.isEmpty()) {
                        connection.writeLine("Unknown race '" + input.trim() + "'. Please try again.");
                        connection.writeLine(svc.buildRacePrompt());
                    } else {
                        creationState = new ChoosingClass(opt.get());
                        connection.writeLine(svc.buildClassPrompt());
                    }
                }
                case ChoosingClass choosing -> {
                    var opt = svc.resolveClassDefinition(input);
                    if (opt.isEmpty()) {
                        connection.writeLine("Unknown class '" + input.trim() + "'. Please try again.");
                        connection.writeLine(svc.buildClassPrompt());
                    } else {
                        finishCharacterCreation(choosing.chosenRace(), opt.get());
                    }
                }
            }
        } catch (CharacterCreationException e) {
            log.error("Character creation error", e);
            connection.writeLine("An error occurred. Please try again.");
        }
    }
    private void finishCharacterCreation(RaceId raceId, ClassDefinition classDef) {
        creationState = null;
        Player player = session.getPlayer();
        if (player == null) {
            return;
        }
        if (raceId != null) {
            player = player.withIdentity(player.identity().withRace(raceId));
            try {
                player = context.characterCreationService().applyRaceStartingStats(player, raceId);
            } catch (CharacterCreationException e) {
                log.warn("Failed to apply race starting stats for {}: {}", raceId.getValue(), e.getMessage());
            }
        }
        if (classDef != null) {
            player = player.withIdentity(player.identity().withClassId(classDef.id()));
            if (!classDef.startingAbilityIds().isEmpty()) {
                player = player.withLearnedAbilities(classDef.startingAbilityIds());
            }
        }
        session.setPlayer(player);
        context.persistenceQueue().enqueueSave(player);
        connection.writeLine("You are now a " + player.getRace().getValue()
            + " " + player.getClassId().getValue() + ". Welcome to the realm!");
        String cid = auditService.newCorrelationId();
        session.enqueueCommand(() -> commandDispatcher.dispatch(commandContext, "look", cid));
    }

    private void handleCommand(String clientInput) {
        String cid = auditService.newCorrelationId();
        boolean accepted = session.enqueueCommand(() -> commandDispatcher.dispatch(commandContext, clientInput, cid));
        if (!accepted) {
            connection.writeLine("You are entering commands too quickly.");
        }
    }
    // Package-private accessors used by SocketCommandContextImpl
    Optional<Username> authenticatedUsername() {
        Player p = session.isAuthenticated() ? session.getPlayer() : null;
        return p == null ? Optional.empty() : Optional.of(p.getUsername());
    }
    boolean isAuthenticatedUser(Username username) {
        return session.isAuthenticated() && session.getPlayer() != null
            && session.getPlayer().getUsername().equals(username);
    }
    void applyExternalPlayerUpdate(Player updated) {
        if (isAuthenticatedUser(updated.getUsername())) {
            session.replacePlayer(updated);
        }
    }

    /**
     * Walks this client one step behind an auto-followed party leader. Pure delegation to the
     * command context (where the movement logic lives, AGENTS.md §3.3); invoked on the tick thread by
     * the leader's move handler so the follower's step lands in the same tick.
     *
     * @param direction  the direction to follow
     * @param leaderName the leader being followed
     */
    void autoFollow(Direction direction, Username leaderName) {
        commandContext.performFollowMove(direction, leaderName);
    }
    PlayerSession session() {
        return session;
    }
}
