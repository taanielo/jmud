package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.ability.AbilityCostResolver;
import io.taanielo.jmud.core.ability.AbilityMatch;
import io.taanielo.jmud.core.ability.AbilityRegistry;
import io.taanielo.jmud.core.ability.AbilityTargetResolver;
import io.taanielo.jmud.core.ability.BasicAbilityCostResolver;
import io.taanielo.jmud.core.ability.RoomAbilityTargetResolver;
import io.taanielo.jmud.core.action.GameActionResult;
import io.taanielo.jmud.core.action.GameActionService;
import io.taanielo.jmud.core.action.GameMessage;
import io.taanielo.jmud.core.audit.AuditEvent;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.AuditSubject;
import io.taanielo.jmud.core.authentication.AuthenticationService;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.authentication.UserRegistry;
import io.taanielo.jmud.core.combat.CombatEngine;
import io.taanielo.jmud.core.combat.CombatModifierResolver;
import io.taanielo.jmud.core.combat.DefaultCombatRandom;
import io.taanielo.jmud.core.combat.repository.AttackRepositoryException;
import io.taanielo.jmud.core.combat.repository.json.JsonAttackRepository;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.effects.repository.json.JsonEffectRepository;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.UserSayMessage;
import io.taanielo.jmud.core.messaging.WelcomeMessage;
import io.taanielo.jmud.core.output.OutputStyleSettings;
import io.taanielo.jmud.core.output.TextStylers;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.prompt.PromptRenderer;
import io.taanielo.jmud.core.prompt.PromptSettings;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.tick.TickRegistry;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomService;

@Slf4j
public class SocketClient implements Client {

    private final TelnetConnection connection;
    private final PlayerSession session;
    private final GameActionService gameActionService;
    private final AuthenticationService authenticationService;
    private final PlayerRepository playerRepository;
    private final RoomService roomService;
    private final ClientPool clientPool;
    private final AbilityRegistry abilityRegistry;
    private final AuditService auditService;
    private final PromptRenderer promptRenderer = new PromptRenderer();
    private final SocketCommandRegistry commandRegistry = new SocketCommandRegistry();
    private final SocketCommandDispatcher commandDispatcher;
    private final ThreadLocal<String> currentCorrelationId = new ThreadLocal<>();

    public SocketClient(
        Socket clientSocket,
        @SuppressWarnings("unused") io.taanielo.jmud.core.messaging.MessageBroadcaster messageBroadcaster,
        UserRegistry userRegistry,
        PlayerRepository playerRepository,
        RoomService roomService,
        TickRegistry tickRegistry,
        ClientPool clientPool,
        AbilityRegistry abilityRegistry,
        AuditService auditService
    ) throws IOException {
        this.connection = new TelnetConnection(clientSocket);
        this.playerRepository = playerRepository;
        this.roomService = roomService;
        this.clientPool = clientPool;
        this.abilityRegistry = abilityRegistry;
        this.auditService = Objects.requireNonNull(auditService, "Audit service is required");
        this.authenticationService = new SocketAuthenticationService(
            clientSocket, userRegistry, connection.messageWriter()
        );

        this.session = new PlayerSession(
            tickRegistry, playerRepository, roomService, this::applyRespawnUpdate
        );

        AbilityTargetResolver targetResolver = new RoomAbilityTargetResolver(roomService, playerRepository);
        AbilityCostResolver costResolver = new BasicAbilityCostResolver();
        EffectEngine effectEngine = createEffectEngine();
        CombatEngine combatEngine = createCombatEngine();

        this.gameActionService = new GameActionService(
            abilityRegistry, costResolver, effectEngine, combatEngine,
            roomService, targetResolver, session.getCooldownTracker()
        );

        this.commandDispatcher = new SocketCommandDispatcher(commandRegistry, auditService);
        registerCommands();
    }

    private void registerCommands() {
        new LookCommand(commandRegistry);
        new MoveCommand(commandRegistry);
        new GetCommand(commandRegistry);
        new DropCommand(commandRegistry);
        new QuaffCommand(commandRegistry);
        new SayCommand(commandRegistry);
        new AbilityCommand(commandRegistry);
        new AttackCommand(commandRegistry);
        new AnsiCommand(commandRegistry);
        new QuitCommand(commandRegistry);
    }

    private EffectEngine createEffectEngine() {
        try {
            return new EffectEngine(new JsonEffectRepository());
        } catch (EffectRepositoryException e) {
            throw new IllegalStateException("Failed to initialize effects: " + e.getMessage(), e);
        }
    }

    private CombatEngine createCombatEngine() {
        try {
            JsonEffectRepository effectRepository = new JsonEffectRepository();
            CombatModifierResolver resolver = new CombatModifierResolver(effectRepository);
            return new CombatEngine(new JsonAttackRepository(), resolver, new DefaultCombatRandom());
        } catch (AttackRepositoryException | EffectRepositoryException e) {
            throw new IllegalStateException("Failed to initialize combat: " + e.getMessage(), e);
        }
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
        session.startTicks();
        int onlineCount = Math.max(0, clientPool.clients().size() - 1);
        sendMessage(WelcomeMessage.of(session.getTextStyler(), onlineCount));
        session.setAuthenticated(false);

        try {
            byte[] bytes = new byte[1024];
            int read;
            while (session.isConnected() && (read = connection.input().read(bytes)) != -1) {
                log.debug("Read: {}", read);
                if (SocketCommand.isIAC(bytes)) {
                    if (SocketCommand.isIP(bytes)) {
                        log.debug("Received IP, closing connection ..");
                        break;
                    } else {
                        log.debug("Received IAC [{}], skipping ..", bytes);
                    }
                    continue;
                }
                String clientInput = SocketCommand.readString(bytes);
                log.debug("Received: \"{}\" [{}]", clientInput, bytes);

                if (!session.isAuthenticated()) {
                    handleAuthentication(clientInput);
                } else {
                    handleCommand(clientInput);
                }
            }
        } catch (IOException e) {
            log.error("Error receiving", e);
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
        session.close();
        connection.close();
        log.debug("Connection closed");
    }

    // ── Authentication ─────────────────────────────────────────────────

    private void handleAuthentication(String clientInput) throws IOException {
        authenticationService.authenticate(clientInput, authenticatedUser -> {
            session.setAuthenticated(true);
            AtomicBoolean isNewPlayer = new AtomicBoolean(false);
            Player player = playerRepository
                .loadPlayer(authenticatedUser.getUsername())
                .orElseGet(() -> {
                    boolean ansiEnabled = OutputStyleSettings.ansiEnabledByDefault();
                    Player newPlayer = Player.of(
                        authenticatedUser,
                        PromptSettings.defaultFormat(),
                        ansiEnabled,
                        abilityRegistry.abilityIds()
                    );
                    isNewPlayer.set(true);
                    playerRepository.savePlayer(newPlayer);
                    return newPlayer;
                });
            if (player.getLearnedAbilities().isEmpty()) {
                player = player.withLearnedAbilities(abilityRegistry.abilityIds());
                playerRepository.savePlayer(player);
            }
            session.setPlayer(player);
            session.setTextStyler(TextStylers.forEnabled(player.isAnsiEnabled()));
            if (player.isDead()) {
                roomService.clearPlayerLocation(player.getUsername());
            } else {
                roomService.ensurePlayerLocation(player.getUsername());
            }
            session.registerEffects(message -> {
                connection.writeLine(message);
                sendPrompt();
            });
            session.registerHealing(this::applyHealingUpdate);
            session.enqueueCommand(session::handleDeathState);
            emitAudit(
                "player.login",
                AuditSubject.player(player.getUsername()),
                null,
                resolveRoomId(player),
                "success",
                Map.of("newPlayer", isNewPlayer.get())
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
        if (updated.getVitals().hp() <= 0 && !updated.isDead()) {
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

    private boolean isAuthenticatedUser(Username username) {
        return session.isAuthenticated()
            && session.getPlayer() != null
            && session.getPlayer().getUsername().equals(username);
    }

    private void applyExternalPlayerUpdate(Player updated) {
        if (!isAuthenticatedUser(updated.getUsername())) {
            return;
        }
        session.replacePlayer(updated);
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
            sendPrompt();
        }

        @Override
        public void sendMove(io.taanielo.jmud.core.world.Direction direction) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to move.");
                return;
            }
            Player player = session.getPlayer();
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
            connection.writeLines(result.lines());
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
        public Optional<Player> resolveTarget(Player source, String input) {
            return new RoomAbilityTargetResolver(roomService, playerRepository).resolve(source, input);
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
