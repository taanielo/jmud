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
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        log.debug("Closing connection ..");
        Player player = session.getPlayer();
        if (session.isAuthenticated() && player != null) {
            commandContext.emitAudit("player.logout", AuditSubject.player(player.getUsername()),
                null, commandContext.resolveRoomId(player), "success",
                Map.of("reason", session.isQuitRequested() ? "quit" : "disconnect"));
        }
        if (context.playerEventBus() != null && session.getPlayer() != null) {
            context.playerEventBus().unregister(session.getPlayer().getUsername());
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
    private void completeAuthentication(User authenticatedUser, boolean isNewPlayer) {
        session.setAuthenticated(true);
        AtomicBoolean created = new AtomicBoolean(isNewPlayer);
        Player player = context.playerRepository()
            .loadPlayer(authenticatedUser.getUsername())
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
        commandContext.emitAudit("player.login", AuditSubject.player(player.getUsername()),
            null, commandContext.resolveRoomId(player), "success", Map.of("newPlayer", created.get()));
        commandContext.registerPostLoginCallbacks(created.get());
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
    PlayerSession session() {
        return session;
    }
}
