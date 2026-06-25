package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityCost;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityMatch;
import io.taanielo.jmud.core.ability.AbilityRegistry;
import io.taanielo.jmud.core.ability.AbilityTargetResolver;
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.creation.CharacterCreationException;
import io.taanielo.jmud.core.creation.CharacterCreationService;
import io.taanielo.jmud.core.creation.CharacterCreationState;
import io.taanielo.jmud.core.creation.CharacterCreationState.ChoosingClass;
import io.taanielo.jmud.core.creation.CharacterCreationState.ChoosingRace;
import io.taanielo.jmud.core.action.GameActionResult;
import io.taanielo.jmud.core.action.GameActionService;
import io.taanielo.jmud.core.action.GameMessage;
import io.taanielo.jmud.core.audit.AuditEvent;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.AuditSubject;
import io.taanielo.jmud.core.authentication.AuthenticationService;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.effects.EffectMessageSink;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.UserSayMessage;
import io.taanielo.jmud.core.messaging.WelcomeBannerMessage;
import io.taanielo.jmud.core.messaging.WelcomeMessage;
import io.taanielo.jmud.core.output.OutputStyleSettings;
import io.taanielo.jmud.core.output.TextStylers;
import io.taanielo.jmud.core.player.EncumbranceService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.prompt.PromptRenderer;
import io.taanielo.jmud.core.prompt.PromptSettings;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.connection.ClientConnection;
import io.taanielo.jmud.core.server.connection.TransportSecurity;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomService;

@Slf4j
public class SocketClient implements Client {

    private final ClientConnection connection;
    private final GameContext context;
    private final PlayerSession session;
    private final GameActionService gameActionService;
    private final AuthenticationService authenticationService;
    private final PlayerRepository playerRepository;
    private final RoomService roomService;
    private final EncumbranceService encumbranceService;
    private final ClientPool clientPool;
    private final AbilityRegistry abilityRegistry;
    private final AbilityTargetResolver abilityTargetResolver;
    private final AuditService auditService;
    private final TransportSecurity transportSecurity;
    private final PromptRenderer promptRenderer = new PromptRenderer();
    private final SocketCommandDispatcher commandDispatcher;
    private final ThreadLocal<String> currentCorrelationId = new ThreadLocal<>();
    private final User preAuthenticatedUser;
    private final boolean preAuthenticatedNewUser;
    private final Runnable onClose;
    private final AtomicBoolean closed = new AtomicBoolean();
    /** Non-null while a new player is choosing race/class; null during normal gameplay. */
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
        Objects.requireNonNull(context, "Game context is required");
        this.context = context;
        this.connection = Objects.requireNonNull(connection, "Connection is required");
        this.authenticationService = Objects.requireNonNull(authenticationService, "Authentication service is required");
        this.playerRepository = context.playerRepository();
        this.roomService = context.roomService();
        this.encumbranceService = context.encumbranceService();
        this.clientPool = clientPool;
        this.abilityRegistry = context.abilityRegistry();
        this.abilityTargetResolver = context.abilityTargetResolver();
        this.auditService = Objects.requireNonNull(context.auditService(), "Audit service is required");
        this.transportSecurity = Objects.requireNonNull(transportSecurity, "Transport security is required");

        this.session = new PlayerSession(
            context.tickRegistry(),
            this.playerRepository,
            this.roomService,
            this::applyRespawnUpdate,
            context.effectEngine(),
            context.effectRepository(),
            context.healingEngine(),
            context.healingBaseResolver()
        );

        this.gameActionService = new GameActionService(
            abilityRegistry,
            context.abilityCostResolver(),
            context.effectEngine(),
            context.combatEngine(),
            roomService,
            abilityTargetResolver,
            session.getCooldownTracker(),
            encumbranceService
        );

        this.commandDispatcher = new SocketCommandDispatcher(context.commandRegistry(), auditService);
        this.preAuthenticatedUser = preAuthenticatedUser;
        this.preAuthenticatedNewUser = preAuthenticatedNewUser;
        this.onClose = onClose;
    }

    // ── Client interface ───────────────────────────────────────────────

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
        sendPrompt();
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
        sendPrompt();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        log.debug("Closing connection ..");
        Player player = session.getPlayer();
        if (session.isAuthenticated() && player != null) {
            Map<String, Object> metadata = Map.of(
                "reason", session.isQuitRequested() ? "quit" : "disconnect"
            );
            emitAudit(
                "player.logout",
                AuditSubject.player(player.getUsername()),
                null,
                resolveRoomId(player),
                "success",
                metadata
            );
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

    // ── Authentication ─────────────────────────────────────────────────

    private void handleAuthentication(String clientInput) throws IOException {
        authenticationService.authenticate(clientInput, authenticatedUser ->
            completeAuthentication(authenticatedUser, false)
        );
    }

    private void completeAuthentication(User authenticatedUser, boolean isNewPlayer) {
        session.setAuthenticated(true);
        AtomicBoolean created = new AtomicBoolean(isNewPlayer);
        Player player = playerRepository
            .loadPlayer(authenticatedUser.getUsername())
            .orElseGet(() -> {
                boolean ansiEnabled = OutputStyleSettings.ansiEnabledByDefault();
                // New players start with no abilities; abilities are seeded after class selection.
                Player newPlayer = Player.of(
                    authenticatedUser,
                    PromptSettings.defaultFormat(),
                    ansiEnabled,
                    List.of()
                );
                created.set(true);
                playerRepository.savePlayer(newPlayer);
                return newPlayer;
            });
        session.setPlayer(player);
        session.setTextStyler(TextStylers.forEnabled(player.isAnsiEnabled()));
        if (player.isDead()) {
            roomService.clearPlayerLocation(player.getUsername());
        } else {
            roomService.ensurePlayerLocation(player.getUsername());
        }
        if (context.playerEventBus() != null) {
            context.playerEventBus().register(player.getUsername(),
                result -> session.enqueueCommand(() -> {
                    deliverResult(result);
                    sendPrompt();
                }));
        }
        session.registerEffects(new EffectMessageSink() {
            @Override
            public void sendToTarget(String message) {
                connection.writeLine(message);
                sendPrompt();
            }

            @Override
            public void sendToRoom(String message) {
                Player current = session.getPlayer();
                if (current == null) {
                    return;
                }
                deliverRoomMessage(current.getUsername(), null, message);
            }
        });
        session.registerHealing(this::applyHealingUpdate);
        session.enqueueCommand(session::handleDeathState);
        emitAudit(
            "player.login",
            AuditSubject.player(player.getUsername()),
            null,
            resolveRoomId(player),
            "success",
            Map.of("newPlayer", created.get())
        );

        if (created.get() && context.characterCreationService() != null) {
            beginCharacterCreation();
        } else {
            String correlationId = auditService.newCorrelationId();
            session.enqueueCommand(() -> {
                currentCorrelationId.set(correlationId);
                try {
                    commandDispatcher.dispatch(new SocketCommandContextImpl(), "look", correlationId);
                } finally {
                    currentCorrelationId.remove();
                }
            });
        }
    }

    // ── Character creation ─────────────────────────────────────────────

    private void beginCharacterCreation() {
        creationState = new ChoosingRace();
        CharacterCreationService svc = context.characterCreationService();
        try {
            String prompt = svc.buildRacePrompt();
            connection.writeLine(prompt);
        } catch (CharacterCreationException e) {
            log.error("Failed to build race prompt", e);
            connection.writeLine("Character creation unavailable. You have been assigned a default race and class.");
            finishCharacterCreation(null, (ClassDefinition) null);
        }
    }

    private void handleCharacterCreation(String input) {
        CharacterCreationService svc = context.characterCreationService();
        if (svc == null) {
            creationState = null;
            return;
        }
        switch (creationState) {
            case ChoosingRace ignored -> {
                try {
                    var raceIdOpt = svc.resolveRace(input);
                    if (raceIdOpt.isEmpty()) {
                        connection.writeLine("Unknown race '" + input.trim() + "'. Please try again.");
                        connection.writeLine(svc.buildRacePrompt());
                    } else {
                        creationState = new ChoosingClass(raceIdOpt.get());
                        connection.writeLine(svc.buildClassPrompt());
                    }
                } catch (CharacterCreationException e) {
                    log.error("Error during race selection", e);
                    connection.writeLine("An error occurred. Please try again.");
                }
            }
            case ChoosingClass choosing -> {
                try {
                    var classDefOpt = svc.resolveClassDefinition(input);
                    if (classDefOpt.isEmpty()) {
                        connection.writeLine("Unknown class '" + input.trim() + "'. Please try again.");
                        connection.writeLine(svc.buildClassPrompt());
                    } else {
                        finishCharacterCreation(choosing.chosenRace(), classDefOpt.get());
                    }
                } catch (CharacterCreationException e) {
                    log.error("Error during class selection", e);
                    connection.writeLine("An error occurred. Please try again.");
                }
            }
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
        playerRepository.savePlayer(player);
        connection.writeLine(
            "You are now a " + player.getRace().getValue()
            + " " + player.getClassId().getValue() + ". Welcome to the realm!"
        );
        String correlationId = auditService.newCorrelationId();
        session.enqueueCommand(() -> {
            currentCorrelationId.set(correlationId);
            try {
                commandDispatcher.dispatch(new SocketCommandContextImpl(), "look", correlationId);
            } finally {
                currentCorrelationId.remove();
            }
        });
    }

    // ── Command dispatch ───────────────────────────────────────────────

    private void handleCommand(String clientInput) {
        String correlationId = auditService.newCorrelationId();
        session.enqueueCommand(() -> {
            currentCorrelationId.set(correlationId);
            try {
                commandDispatcher.dispatch(new SocketCommandContextImpl(), clientInput, correlationId);
            } finally {
                currentCorrelationId.remove();
            }
        });
    }

    // ── Game action result delivery ────────────────────────────────────

    private void deliverMessages(List<GameMessage> messages) {
        for (GameMessage msg : messages) {
            switch (msg.type()) {
                case SOURCE -> connection.writeLine(msg.text());
                case PLAYER -> sendToUsername(msg.targetExclude(), msg.text());
                case ROOM -> deliverRoomMessage(msg.sourceExclude(), msg.targetExclude(), msg.text());
            }
        }
    }

    private void deliverResult(GameActionResult result) {
        deliverMessages(result.messages());
        if (result.updatedSource() != null) {
            session.replacePlayer(result.updatedSource());
        }
        if (result.updatedTarget() != null) {
            Player current = session.getPlayer();
            if (current != null && !result.updatedTarget().getUsername().equals(current.getUsername())) {
                updateTarget(result.updatedTarget());
            }
        }
    }

    private void updateTarget(Player updatedTarget) {
        playerRepository.savePlayer(updatedTarget);
        for (Client client : clientPool.clients()) {
            if (client instanceof SocketClient socketClient) {
                if (socketClient.isAuthenticatedUser(updatedTarget.getUsername())) {
                    socketClient.applyExternalPlayerUpdate(updatedTarget);
                    return;
                }
            }
        }
    }

    // ── Tick callbacks ─────────────────────────────────────────────────

    private void applyHealingUpdate(Player updated) {
        Player current = session.getPlayer();
        if (current != null) {
            int beforeHp = current.getVitals().hp();
            int afterHp = updated.getVitals().hp();
            int delta = afterHp - beforeHp;
            if (delta != 0) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("amount", Math.abs(delta));
                metadata.put("hpBefore", beforeHp);
                metadata.put("hpAfter", afterHp);
                String eventType = delta > 0 ? "healing.tick" : "damage.tick";
                emitAudit(
                    eventType,
                    AuditSubject.player(current.getUsername()),
                    null,
                    resolveRoomId(current),
                    "success",
                    metadata
                );
            }
        }
        if (updated.getVitals().hp() <= 0 && (current == null || !current.isDead())) {
            GameActionResult deathResult = gameActionService.resolveDeathIfNeeded(updated, null);
            deliverMessages(deathResult.messages());
            auditDeath(deathResult.updatedTarget(), null);
            session.replacePlayer(deathResult.updatedTarget());
            return;
        }
        session.setPlayer(updated);
        playerRepository.savePlayer(updated);
    }

    private void applyRespawnUpdate(Player updated) {
        session.replacePlayer(updated);
        connection.writeLine("You awaken in the starting room.");
        Player player = session.getPlayer();
        RoomService.LookResult result = roomService.look(player.getUsername());
        emitAudit(
            "player.respawn",
            AuditSubject.player(player.getUsername()),
            null,
            result.room() == null ? null : result.room().getId().getValue(),
            "success",
            Map.of()
        );
        connection.writeLines(result.lines());
        if (context.mobRegistry() != null && result.room() != null) {
            var mobs = context.mobRegistry().getMobsInRoom(result.room().getId());
            connection.writeLine(formatMonstersLine(mobs));
        }
        sendPrompt();
    }

    // ── ANSI command ───────────────────────────────────────────────────

    private void handleAnsiCommand(String args) {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            writeLineWithPrompt("You must be logged in to change ANSI settings.");
            return;
        }
        String normalized = args == null ? "" : args.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.equals("STATUS")) {
            writeLineWithPrompt("ANSI is " + (player.isAnsiEnabled() ? "ON" : "OFF"));
            return;
        }
        switch (normalized) {
            case "ON" -> setAnsiEnabled(true);
            case "OFF" -> setAnsiEnabled(false);
            case "TOGGLE" -> setAnsiEnabled(!player.isAnsiEnabled());
            default -> writeLineWithPrompt("Usage: ANSI [on|off|toggle|status]");
        }
    }

    private void setAnsiEnabled(boolean enabled) {
        Player player = session.getPlayer();
        if (player.isAnsiEnabled() == enabled) {
            writeLineWithPrompt("ANSI is already " + (enabled ? "ON" : "OFF"));
            return;
        }
        session.replacePlayer(player.withAnsiEnabled(enabled));
        session.setTextStyler(TextStylers.forEnabled(session.getPlayer().isAnsiEnabled()));
        writeLineWithPrompt("ANSI is now " + (enabled ? "ON" : "OFF"));
    }

    // ── I/O helpers ────────────────────────────────────────────────────

    private void writeLineWithPrompt(String message) {
        connection.writeLine(message);
        sendPrompt();
    }

    private void sendPrompt() {
        Player player = session.getPlayer();
        if (!session.isAuthenticated() || player == null) {
            return;
        }
        String format = player.getPromptFormat();
        if (format == null || format.isBlank()) {
            format = PromptSettings.defaultFormat();
        }
        String promptLine = promptRenderer.render(format, player);
        connection.write(promptLine + "> ");
    }

    private void sendToUsername(Username username, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        for (Client client : clientPool.clients()) {
            if (client instanceof SocketClient socketClient) {
                if (socketClient.isAuthenticatedUser(username)) {
                    socketClient.connection.writeLine(message);
                    return;
                }
            }
        }
    }

    private void sendToRoom(Player source, Player target, String message) {
        deliverRoomMessage(source.getUsername(), target.getUsername(), message);
    }

    private void sendToRoom(Room room, Username exclude, String message) {
        if (message == null || message.isBlank() || room == null) {
            return;
        }
        if (room.getOccupants().isEmpty()) {
            return;
        }
        for (Username occupant : room.getOccupants()) {
            if (exclude != null && occupant.equals(exclude)) {
                continue;
            }
            sendToUsername(occupant, message);
        }
    }

    private void deliverRoomMessage(Username sourceExclude, Username targetExclude, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        Username lookupUser = sourceExclude != null
            ? sourceExclude
            : (session.getPlayer() != null ? session.getPlayer().getUsername() : null);
        if (lookupUser == null) {
            return;
        }
        RoomService.LookResult look = roomService.look(lookupUser);
        Room room = look.room();
        if (room == null || room.getOccupants().isEmpty()) {
            return;
        }
        for (Username occupant : room.getOccupants()) {
            if ((sourceExclude != null && occupant.equals(sourceExclude))
                || (targetExclude != null && occupant.equals(targetExclude))) {
                continue;
            }
            sendToUsername(occupant, message);
        }
    }

    // ── Auditing ───────────────────────────────────────────────────────

    private void emitAudit(
        String eventType,
        AuditSubject actor,
        AuditSubject target,
        String roomId,
        String result,
        Map<String, Object> metadata
    ) {
        String correlationId = currentCorrelationId.get();
        AuditEvent event = new AuditEvent(
            eventType,
            actor,
            target,
            roomId,
            result,
            correlationId == null ? auditService.newCorrelationId() : correlationId,
            metadata
        );
        auditService.emit(event);
    }

    private void auditDeath(Player deadTarget, Player attacker) {
        if (deadTarget == null) {
            return;
        }
        AuditSubject actor = attacker == null
            ? AuditSubject.system("environment")
            : AuditSubject.player(attacker.getUsername());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("cause", attacker == null ? "effect" : "combat");
        emitAudit(
            "player.death",
            actor,
            AuditSubject.player(deadTarget.getUsername()),
            resolveRoomId(deadTarget),
            "success",
            metadata
        );
    }

    private String resolveRoomId(Player player) {
        if (player == null) {
            return null;
        }
        return roomService.findPlayerLocation(player.getUsername())
            .map(roomId -> roomId.getValue())
            .orElse(null);
    }

    // ── Utility ────────────────────────────────────────────────────────

    /**
     * Formats a {@code Monsters:} summary line for the given live mob instances.
     *
     * <p>Mobs of the same template name are grouped and counted; e.g.
     * {@code "Monsters: 2x Goblin, 1x Troll"}. Returns {@code "Monsters: none"}
     * when the list is empty.
     *
     * @param mobs live mob instances currently in the room
     * @return formatted Monsters line
     */
    static String formatMonstersLine(java.util.List<io.taanielo.jmud.core.mob.MobInstance> mobs) {
        if (mobs == null || mobs.isEmpty()) {
            return "Monsters: none";
        }
        // Group by template name, preserving encounter order for determinism.
        java.util.LinkedHashMap<String, Long> counts = mobs.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                m -> m.template().name(),
                java.util.LinkedHashMap::new,
                java.util.stream.Collectors.counting()
            ));
        String body = counts.entrySet().stream()
            .map(e -> e.getValue() + "x " + e.getKey())
            .collect(java.util.stream.Collectors.joining(", "));
        return "Monsters: " + body;
    }

    private boolean isAuthenticatedUser(Username username) {
        return session.isAuthenticated()
            && session.getPlayer() != null
            && session.getPlayer().getUsername().equals(username);
    }

    /**
     * Returns the authenticated player's username for this client, or empty when
     * the client is still at the login stage. Used to build the online roster.
     */
    private Optional<Username> authenticatedUsername() {
        if (!session.isAuthenticated()) {
            return Optional.empty();
        }
        Player player = session.getPlayer();
        return player == null ? Optional.empty() : Optional.of(player.getUsername());
    }

    private void applyExternalPlayerUpdate(Player updated) {
        if (!isAuthenticatedUser(updated.getUsername())) {
            return;
        }
        session.replacePlayer(updated);
    }

    private String formatLookDescription(Player target) {
        String name = target.getUsername().getValue();
        String race = target.getRace().getValue();
        String classId = target.getClassId().getValue();
        return name + " the " + race + " " + classId + " (level " + target.getLevel() + ").";
    }

    // ── Command context ────────────────────────────────────────────────

    private class SocketCommandContextImpl implements SocketCommandContext {
        @Override
        public boolean isAuthenticated() {
            return session.isAuthenticated();
        }

        @Override
        public Player getPlayer() {
            return session.getPlayer();
        }

        @Override
        public List<Client> clients() {
            return clientPool.clients();
        }

        @Override
        public List<Username> onlinePlayerNames() {
            return clientPool.clients().stream()
                .filter(SocketClient.class::isInstance)
                .map(SocketClient.class::cast)
                .map(SocketClient::authenticatedUsername)
                .flatMap(Optional::stream)
                .toList();
        }

        @Override
        public void sendMessage(Message message) {
            SocketClient.this.sendMessage(message);
        }

        @Override
        public void close() {
            session.setQuitRequested(true);
            SocketClient.this.close();
        }

        @Override
        public void run() {
            SocketClient.this.run();
        }

        @Override
        public void sendLook() {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to look around.");
                return;
            }
            RoomService.LookResult result = roomService.look(session.getPlayer().getUsername());
            connection.writeLines(result.lines());
            if (context.mobRegistry() != null && result.room() != null) {
                var mobs = context.mobRegistry().getMobsInRoom(result.room().getId());
                connection.writeLine(formatMonstersLine(mobs));
            }
            sendPrompt();
        }

        @Override
        public void sendLookAt(String targetInput) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to look around.");
                return;
            }
            if (targetInput == null || targetInput.isBlank()) {
                writeLineWithPrompt("Look at whom?");
                return;
            }
            Player source = session.getPlayer();
            Optional<Player> resolved = abilityTargetResolver.resolve(source, targetInput);
            if (resolved.isEmpty()) {
                writeLineWithPrompt("You don't see that here.");
                return;
            }
            Player target = resolved.get();
            String description = formatLookDescription(target);
            connection.writeLine(description);
            if (!target.getUsername().equals(source.getUsername())) {
                String sourceName = source.getUsername().getValue();
                sendToUsername(target.getUsername(), sourceName + " looks at you.");
                String roomMessage = sourceName + " looks at " + target.getUsername().getValue() + ".";
                deliverRoomMessage(source.getUsername(), target.getUsername(), roomMessage);
            }
            sendPrompt();
        }

        @Override
        public void sendMove(io.taanielo.jmud.core.world.Direction direction) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to move.");
                return;
            }
            Player player = session.getPlayer();
            if (encumbranceService.isOverburdened(player)) {
                writeLineWithPrompt("You are carrying too much to do that.");
                return;
            }
            RoomService.LookResult currentLook = roomService.look(player.getUsername());
            Room oldRoom = currentLook.room();
            String fromRoom = resolveRoomId(player);
            RoomService.MoveResult result = roomService.move(player.getUsername(), direction);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("direction", direction.label());
            metadata.put("fromRoom", fromRoom);
            metadata.put("toRoom", result.room() == null ? null : result.room().getId().getValue());
            emitAudit(
                "player.move",
                AuditSubject.player(player.getUsername()),
                null,
                result.room() == null ? fromRoom : result.room().getId().getValue(),
                result.moved() ? "success" : "blocked",
                metadata
            );
            if (result.moved()) {
                if (oldRoom != null) {
                    String leaveMessage = player.getUsername().getValue()
                        + " leaves " + direction.label() + ".";
                    SocketClient.this.sendToRoom(oldRoom, player.getUsername(), leaveMessage);
                }
                String arriveMessage = player.getUsername().getValue() + " arrives.";
                deliverRoomMessage(player.getUsername(), null, arriveMessage);
            }
            connection.writeLines(result.lines());
            if (result.moved() && context.mobRegistry() != null && result.room() != null) {
                var mobs = context.mobRegistry().getMobsInRoom(result.room().getId());
                connection.writeLine(formatMonstersLine(mobs));
            }
            sendPrompt();
        }

        @Override
        public void useAbility(String args) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to use abilities.");
                return;
            }
            Player player = session.getPlayer();
            AbilityMatch match = abilityRegistry
                .findBestMatch(args, player.getLearnedAbilities()).orElse(null);
            GameActionResult result = gameActionService.useAbility(player, args);
            auditAbilityUse(match, result, args);
            deliverResult(result);
            sendPrompt();
        }

        @Override
        public void updateAnsi(String args) {
            handleAnsiCommand(args);
        }

        @Override
        public void writeLineWithPrompt(String message) {
            SocketClient.this.writeLineWithPrompt(message);
        }

        @Override
        public void writeLineSafe(String message) {
            connection.writeLine(message);
        }

        @Override
        public void sendToUsername(Username username, String message) {
            SocketClient.this.sendToUsername(username, message);
        }

        @Override
        public void sendPrompt() {
            SocketClient.this.sendPrompt();
        }

        @Override
        public void sendToRoom(Player source, Player target, String message) {
            SocketClient.this.sendToRoom(source, target, message);
        }

        @Override
        public void sendToRoom(Player source, String message) {
            if (source == null) {
                return;
            }
            deliverRoomMessage(source.getUsername(), null, message);
        }

        @Override
        public Optional<Player> resolveTarget(Player source, String input) {
            return abilityTargetResolver.resolve(source, input);
        }

        @Override
        public void executeAttack(String args) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to attack.");
                return;
            }
            GameActionResult result = gameActionService.attack(session.getPlayer(), args);
            auditAttack(result, args);
            deliverResult(result);
            sendPrompt();
        }

        @Override
        public void getItem(String args) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to get items.");
                return;
            }
            GameActionResult result = gameActionService.getItem(session.getPlayer(), args);
            deliverResult(result);
            sendPrompt();
        }

        @Override
        public void dropItem(String args) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to drop items.");
                return;
            }
            GameActionResult result = gameActionService.dropItem(session.getPlayer(), args);
            deliverResult(result);
            sendPrompt();
        }

        @Override
        public void quaffItem(String args) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to quaff.");
                return;
            }
            GameActionResult result = gameActionService.quaffItem(session.getPlayer(), args);
            deliverResult(result);
            sendPrompt();
        }

        @Override
        public void equipItem(String args) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to equip items.");
                return;
            }
            GameActionResult result = gameActionService.equipItem(session.getPlayer(), args);
            deliverResult(result);
            sendPrompt();
        }

        @Override
        public void unequipItem(String args) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to unequip items.");
                return;
            }
            GameActionResult result = gameActionService.unequipSlot(session.getPlayer(), args);
            deliverResult(result);
            sendPrompt();
        }

        @Override
        public void killMob(String args) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to attack.");
                return;
            }
            if (context.mobRegistry() == null) {
                writeLineWithPrompt("There are no mobs here.");
                return;
            }
            Player player = session.getPlayer();
            var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
            if (roomIdOpt.isEmpty()) {
                writeLineWithPrompt("You are nowhere.");
                return;
            }
            GameActionResult result = context.mobRegistry()
                .processPlayerAttack(player, args, roomIdOpt.get());
            deliverResult(result);
            sendPrompt();
        }

        @Override
        public void fleeCombat() {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to flee.");
                return;
            }
            if (context.mobRegistry() == null) {
                writeLineWithPrompt("You are not in combat.");
                return;
            }
            Player player = session.getPlayer();
            if (!context.mobRegistry().isInCombat(player.getUsername())) {
                writeLineWithPrompt("You are not in combat.");
                return;
            }
            RoomService.LookResult lookResult = roomService.look(player.getUsername());
            Room currentRoom = lookResult.room();
            if (currentRoom == null || currentRoom.getExits().isEmpty()) {
                writeLineWithPrompt("There is nowhere to flee!");
                return;
            }
            List<Direction> exits = new ArrayList<>(currentRoom.getExits().keySet());
            Direction chosen = exits.get(ThreadLocalRandom.current().nextInt(exits.size()));
            connection.writeLine("You flee to the " + chosen.label() + "!");
            context.mobRegistry().fleeCombat(player.getUsername());
            sendMove(chosen);
        }

        @Override
        public void sendInventory() {
            Player player = session.getPlayer();
            if (!session.isAuthenticated() || player == null) {
                writeLineWithPrompt("You must be logged in to check your inventory.");
                return;
            }
            int carried = encumbranceService.carriedWeight(player);
            int maxCarry = encumbranceService.maxCarry(player);
            for (String line : InventoryListing.format(player.getInventory(), carried, maxCarry)) {
                connection.writeLine(line);
            }
            sendPrompt();
        }

        @Override
        public void sendEquipment() {
            Player player = session.getPlayer();
            if (!session.isAuthenticated() || player == null) {
                writeLineWithPrompt("You must be logged in to check your equipment.");
                return;
            }
            Map<ItemId, Item> inventoryIndex = player.getInventory().stream()
                .collect(Collectors.toMap(Item::getId, i -> i, (a, b) -> a));
            for (String line : EquipmentListing.format(player.getEquipment().slots(), inventoryIndex::get)) {
                connection.writeLine(line);
            }
            sendPrompt();
        }

        @Override
        public void gossip(String senderName, String message) {
            for (Client client : clientPool.clients()) {
                if (client instanceof SocketClient socketClient) {
                    Optional<Username> usernameOpt = socketClient.authenticatedUsername();
                    if (usernameOpt.isEmpty()) {
                        continue;
                    }
                    Username recipient = usernameOpt.get();
                    // Skip the sender — they already see "You gossip: ..." from GossipCommand.
                    if (recipient.equals(Username.of(senderName))) {
                        continue;
                    }
                    socketClient.connection.writeLine(senderName + " gossips: " + message);
                    socketClient.sendPrompt();
                }
            }
        }

        @Override
        public void sendAbilities() {
            Player player = session.getPlayer();
            if (!session.isAuthenticated() || player == null) {
                writeLineWithPrompt("You must be logged in to view your abilities.");
                return;
            }
            List<AbilityId> learned = player.getLearnedAbilities();
            if (learned.isEmpty()) {
                writeLineWithPrompt("You have not learned any abilities yet.");
                return;
            }
            connection.writeLine(String.format("%-20s %-6s %-12s %s", "Ability", "Type", "Cost", "Cooldown"));
            connection.writeLine("-".repeat(52));
            for (AbilityId abilityId : learned) {
                Ability ability = abilityRegistry.findById(abilityId).orElse(null);
                if (ability == null) {
                    continue;
                }
                AbilityCost cost = ability.cost();
                String costStr;
                if (cost.mana() > 0 && cost.move() > 0) {
                    costStr = cost.mana() + " mana, " + cost.move() + " mv";
                } else if (cost.mana() > 0) {
                    costStr = cost.mana() + " mana";
                } else if (cost.move() > 0) {
                    costStr = cost.move() + " mv";
                } else {
                    costStr = "none";
                }
                String cooldownStr = ability.cooldown().ticks() > 0
                    ? ability.cooldown().ticks() + " ticks"
                    : "none";
                connection.writeLine(String.format("%-20s %-6s %-12s %s",
                    ability.name(), ability.type().name(), costStr, cooldownStr));
            }
            sendPrompt();
        }

        @Override
        public void examineItem(String args) {
            Player player = session.getPlayer();
            if (!session.isAuthenticated() || player == null) {
                writeLineWithPrompt("You must be logged in to examine items.");
                return;
            }
            if (args == null || args.isBlank()) {
                writeLineWithPrompt("Examine what?");
                return;
            }
            // Search inventory first, then room items.
            Item found = matchItemByName(player.getInventory(), args);
            if (found == null) {
                RoomService.LookResult look = roomService.look(player.getUsername());
                if (look.room() != null) {
                    found = matchItemByName(look.room().getItems(), args);
                }
            }
            if (found == null) {
                writeLineWithPrompt("You don't see '" + args.trim() + "' here.");
                return;
            }
            connection.writeLine(found.getName());
            connection.writeLine(found.getDescription());
            if (found.getEquipSlot() != null) {
                connection.writeLine("Slot: " + found.getEquipSlot().id());
            }
            if (found.getAttributes() != null && !found.getAttributes().getStats().isEmpty()) {
                StringBuilder sb = new StringBuilder("Stats:");
                found.getAttributes().getStats().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> sb.append(" ").append(e.getKey()).append(" +").append(e.getValue()));
                connection.writeLine(sb.toString());
            }
            sendPrompt();
        }
    }

    // ── Item helpers ───────────────────────────────────────────────────

    /**
     * Returns the first item in the list whose name or id starts with (or equals)
     * the normalised input, or {@code null} when no match is found.
     */
    private static Item matchItemByName(List<Item> items, String input) {
        String normalized = input.trim().toLowerCase(java.util.Locale.ROOT);
        for (Item item : items) {
            String name = item.getName().toLowerCase(java.util.Locale.ROOT);
            if (name.equals(normalized) || name.startsWith(normalized)) {
                return item;
            }
            String id = item.getId().getValue().toLowerCase(java.util.Locale.ROOT);
            if (id.equals(normalized) || id.startsWith(normalized)) {
                return item;
            }
        }
        return null;
    }

    // ── Audit helpers ──────────────────────────────────────────────────

    private void auditAbilityUse(AbilityMatch match, GameActionResult result, String input) {
        Player player = session.getPlayer();
        if (player == null || result == null) {
            return;
        }
        Map<String, Object> metadata = new HashMap<>();
        if (input != null && !input.isBlank()) {
            metadata.put("input", input.trim());
        }
        if (match != null) {
            metadata.put("abilityId", match.ability().id().getValue());
            metadata.put("abilityName", match.ability().name());
            String remainingTarget = match.remainingTarget();
            if (remainingTarget != null && !remainingTarget.isBlank()) {
                metadata.put("targetInput", remainingTarget);
            }
        }
        AuditSubject target = result.updatedTarget() == null
            ? null
            : AuditSubject.player(result.updatedTarget().getUsername());
        emitAudit(
            "ability.use",
            AuditSubject.player(player.getUsername()),
            target,
            resolveRoomId(player),
            "attempted",
            metadata
        );
    }

    private void auditAttack(GameActionResult result, String args) {
        Player player = session.getPlayer();
        if (player == null) {
            return;
        }
        if (result.updatedTarget() == null) {
            return;
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("targetName", result.updatedTarget().getUsername().getValue());
        if (args != null && !args.isBlank()) {
            metadata.put("input", args.trim());
        }
        emitAudit(
            "combat.attack",
            AuditSubject.player(player.getUsername()),
            AuditSubject.player(result.updatedTarget().getUsername()),
            resolveRoomId(player),
            "attempted",
            metadata
        );
    }
}
