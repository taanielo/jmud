package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import io.taanielo.jmud.core.action.GameActionResult;
import io.taanielo.jmud.core.action.GameActionService;
import io.taanielo.jmud.core.action.GameMessage;
import io.taanielo.jmud.core.audit.AuditEvent;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.AuditSubject;
import io.taanielo.jmud.core.authentication.AuthenticationService;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.bank.BankTransactionResult;
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.creation.CharacterCreationException;
import io.taanielo.jmud.core.creation.CharacterCreationService;
import io.taanielo.jmud.core.creation.CharacterCreationState;
import io.taanielo.jmud.core.creation.CharacterCreationState.ChoosingClass;
import io.taanielo.jmud.core.creation.CharacterCreationState.ChoosingRace;
import io.taanielo.jmud.core.effects.EffectMessageSink;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.messaging.UserSayMessage;
import io.taanielo.jmud.core.messaging.WelcomeBannerMessage;
import io.taanielo.jmud.core.messaging.WelcomeMessage;
import io.taanielo.jmud.core.output.OutputStyleSettings;
import io.taanielo.jmud.core.output.TextStylers;
import io.taanielo.jmud.core.party.Party;
import io.taanielo.jmud.core.party.PartyService;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.EncumbranceService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.player.RestSettings;
import io.taanielo.jmud.core.player.RestingTicker;
import io.taanielo.jmud.core.prompt.PromptRenderer;
import io.taanielo.jmud.core.prompt.PromptSettings;
import io.taanielo.jmud.core.quest.ActiveQuest;
import io.taanielo.jmud.core.quest.QuestDeliveryService;
import io.taanielo.jmud.core.quest.QuestRepository;
import io.taanielo.jmud.core.quest.QuestRepositoryException;
import io.taanielo.jmud.core.quest.QuestTemplate;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.connection.ClientConnection;
import io.taanielo.jmud.core.server.connection.TransportSecurity;
import io.taanielo.jmud.core.shop.ShopTransactionResult;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;

@Slf4j
public class SocketClient implements Client {

    private final ClientConnection connection;
    private final GameContext context;
    private final PlayerSession session;
    private final GameActionService gameActionService;
    private final AuthenticationService authenticationService;
    private final PlayerRepository playerRepository;
    private final PersistenceQueue persistenceQueue;
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
        this.persistenceQueue = context.persistenceQueue();
        this.roomService = context.roomService();
        this.encumbranceService = context.encumbranceService();
        this.clientPool = clientPool;
        this.abilityRegistry = context.abilityRegistry();
        this.abilityTargetResolver = context.abilityTargetResolver();
        this.auditService = Objects.requireNonNull(context.auditService(), "Audit service is required");
        this.transportSecurity = Objects.requireNonNull(transportSecurity, "Transport security is required");

        this.session = new PlayerSession(
            context.tickRegistry(),
            this.persistenceQueue,
            this.roomService,
            this::applyRespawnUpdate,
            context.effectEngine(),
            context.effectRepository(),
            context.healingEngine(),
            context.healingBaseResolver()
        );
        this.session.setSaveFailureHandler(this::handleSaveFailure);

        this.gameActionService = new GameActionService(
            abilityRegistry,
            context.abilityCostResolver(),
            context.effectEngine(),
            context.combatEngine(),
            roomService,
            abilityTargetResolver,
            session.getCooldownTracker(),
            encumbranceService,
            p -> context.mobRegistry() != null
                && context.mobRegistry().isInCombat(p.getUsername())
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

    @Override
    public Optional<Player> currentPlayer() {
        return session.isAuthenticated() ? Optional.ofNullable(session.getPlayer()) : Optional.empty();
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
                saveOrWarn(newPlayer);
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
                    // If a mob hit updates the player and the player was resting, cancel rest first.
                    if (result.updatedSource() != null) {
                        Player current = session.getPlayer();
                        if (current != null && current.isResting()) {
                            session.setPlayer(current.withResting(false));
                            session.clearRestingTicker();
                            connection.writeLine("You are jolted awake by the attack!");
                        }
                    }
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
        saveOrWarn(player);
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
        saveOrWarn(updatedTarget);
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
        saveOrWarn(updated);
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
        if (result.room() != null) {
            if (context.mobRegistry() != null) {
                var mobs = context.mobRegistry().getMobsInRoom(result.room().getId());
                connection.writeLine(formatMonstersLine(mobs));
            }
            String shopLine = formatShopNpcLine(result.room().getId());
            if (!shopLine.isEmpty()) {
                connection.writeLine(shopLine);
            }
            String bankLine = formatBankNpcLine(result.room().getId());
            if (!bankLine.isEmpty()) {
                connection.writeLine(bankLine);
            }
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

    /**
     * Silently cancels resting if the current player is resting.
     * The caller is responsible for sending any relevant message afterwards.
     */
    private void cancelRestIfActive() {
        Player player = session.getPlayer();
        if (player != null && player.isResting()) {
            session.setPlayer(player.withResting(false));
            session.clearRestingTicker();
            connection.writeLine("Your rest is interrupted.");
        }
    }

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
        String partyHp = format.contains("{partyHp}") ? buildPartyHpString(player) : "";
        String promptLine = promptRenderer.render(format, player, partyHp);
        connection.write(promptLine + "> ");
    }

    /**
     * Builds the {@code {partyHp}} token value for the given player.
     *
     * <p>Iterates the party members, looks up current HP from connected sessions
     * (falling back to the repository), and returns a space-separated
     * {@code Name:hp/maxHp} string. Returns an empty string when the player
     * is not in a party.
     *
     * @param player the current player
     * @return formatted party HP string, or {@code ""} when not in a party
     */
    private String buildPartyHpString(Player player) {
        PartyService partyService = context.partyService();
        if (partyService == null) {
            return "";
        }
        return partyService.findParty(player.getUsername())
            .map(party -> {
                StringBuilder sb = new StringBuilder();
                for (Username memberId : party.memberIds()) {
                    if (memberId.equals(player.getUsername())) {
                        continue;
                    }
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(memberId.getValue()).append(':').append(findMemberCurrentHp(memberId));
                }
                return sb.toString();
            })
            .orElse("");
    }

    /**
     * Returns the current HP string for a party member by consulting connected sessions
     * first, then falling back to the player repository.
     *
     * @param username the member to look up
     * @return {@code "hp/maxHp"} string
     */
    private String findMemberCurrentHp(Username username) {
        for (Client client : clientPool.clients()) {
            if (client instanceof SocketClient sc && sc.isAuthenticatedUser(username)) {
                Player p = sc.session.getPlayer();
                if (p != null) {
                    return p.getVitals().hp() + "/" + p.getVitals().maxHp();
                }
            }
        }
        return playerRepository.loadPlayer(username)
            .map(p -> p.getVitals().hp() + "/" + p.getVitals().maxHp())
            .orElse("?/?");
    }

    private void sendToUsername(Username username, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        context.messageBroadcaster().sendToPlayer(username, new PlainTextMessage(message));
    }

    private void sendToRoom(Player source, Player target, String message) {
        deliverRoomMessage(source.getUsername(), target.getUsername(), message);
    }

    private void sendToRoom(Room room, Username exclude, String message) {
        if (message == null || message.isBlank() || room == null) {
            return;
        }
        Set<Username> excludeSet = exclude == null ? Set.of() : Set.of(exclude);
        context.messageBroadcaster().broadcastToRoom(room.getId(), new PlainTextMessage(message), excludeSet);
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
        Optional<RoomId> roomIdOpt = roomService.findPlayerLocation(lookupUser);
        if (roomIdOpt.isEmpty()) {
            return;
        }
        Set<Username> excludeSet = new HashSet<>();
        if (sourceExclude != null) {
            excludeSet.add(sourceExclude);
        }
        if (targetExclude != null) {
            excludeSet.add(targetExclude);
        }
        context.messageBroadcaster().broadcastToRoom(roomIdOpt.get(), new PlainTextMessage(message), excludeSet);
    }

    // ── Persistence ─────────────────────────────────────────────────────

    /**
     * Hands the given player off to the write-behind persistence queue rather than
     * saving synchronously on this (tick) thread (AGENTS.md §5). Failures are logged
     * and audited by the queue itself after its retry; there is no more a way to warn
     * the player synchronously about a failed save from this call site.
     *
     * @param playerToSave the player to save
     */
    private void saveOrWarn(Player playerToSave) {
        persistenceQueue.enqueueSave(playerToSave);
    }

    /**
     * Handles a failed flush of pending saves on disconnect: logs the failure, warns
     * the player, and emits an audit event. Passed to {@link PlayerSession} as its
     * save-failure hook.
     *
     * @param player the player whose save failed to flush before disconnect
     */
    private void handleSaveFailure(Player player) {
        log.error("Player save failed for {}; warning player", player.getUsername());
        connection.writeLine("Warning: your progress could not be saved.");
        emitAudit(
            "player.save.failed",
            AuditSubject.player(player.getUsername()),
            null,
            resolveRoomId(player),
            "failure",
            Map.of()
        );
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

    /**
     * Builds a shopkeeper NPC line for the given room, using the context's shop service.
     *
     * <p>Returns an empty string when there is no shop in the room or the service is unavailable.
     *
     * @param roomId the room to check for a shop
     * @return a formatted line such as {@code "Merchant: Torbal the Armorer"}, or {@code ""}
     */
    private String formatShopNpcLine(RoomId roomId) {
        if (context.shopService() == null || roomId == null) {
            return "";
        }
        return context.shopService()
            .findShopInRoom(roomId)
            .map(shop -> "Merchant: " + shop.name())
            .orElse("");
    }

    /**
     * Builds a bank NPC line for the given room, using the context's bank service.
     *
     * <p>Returns an empty string when there is no bank in the room or the service is unavailable.
     *
     * @param roomId the room to check for a bank
     * @return a formatted line such as {@code "Banker: Aldric the Banker"}, or {@code ""}
     */
    private String formatBankNpcLine(RoomId roomId) {
        if (context.bankService() == null || roomId == null) {
            return "";
        }
        return context.bankService()
            .findBankInRoom(roomId)
            .map(bank -> "Banker: " + bank.name())
            .orElse("");
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
            if (result.room() != null) {
                String shopLine = formatShopNpcLine(result.room().getId());
                if (!shopLine.isEmpty()) {
                    connection.writeLine(shopLine);
                }
                String bankLine = formatBankNpcLine(result.room().getId());
                if (!bankLine.isEmpty()) {
                    connection.writeLine(bankLine);
                }
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
            cancelRestIfActive();
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
            if (result.moved() && result.room() != null) {
                if (context.mobRegistry() != null) {
                    var mobs = context.mobRegistry().getMobsInRoom(result.room().getId());
                    connection.writeLine(formatMonstersLine(mobs));
                }
                String shopLine = formatShopNpcLine(result.room().getId());
                if (!shopLine.isEmpty()) {
                    connection.writeLine(shopLine);
                }
                String bankLine = formatBankNpcLine(result.room().getId());
                if (!bankLine.isEmpty()) {
                    connection.writeLine(bankLine);
                }
            }
            sendPrompt();
        }

        @Override
        public void useAbility(String args) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to use abilities.");
                return;
            }
            cancelRestIfActive();
            Player player = session.getPlayer();
            AbilityMatch match = abilityRegistry
                .findBestMatch(args, player.getLearnedAbilities()).orElse(null);
            GameActionResult result = gameActionService.useAbility(player, args);
            auditAbilityUse(match, result, args);
            deliverResult(result);
            sendPrompt();
        }

        @Override
        public void castSpell(String args) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to cast spells.");
                return;
            }
            cancelRestIfActive();
            Player player = session.getPlayer();
            AbilityMatch match = abilityRegistry
                .findBestMatch(args, player.getLearnedAbilities()).orElse(null);
            if (match != null && match.ability().type() != io.taanielo.jmud.core.ability.AbilityType.SPELL) {
                writeLineWithPrompt("That is not a spell.");
                return;
            }
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
            cancelRestIfActive();
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
            cancelRestIfActive();
            Player playerBeforeGet = session.getPlayer();
            GameActionResult result = gameActionService.getItem(playerBeforeGet, args);
            deliverResult(result);
            // After a successful pickup, check if this triggers delivery quest progress
            if (result.updatedSource() != null && context.questRepository() != null) {
                Player updatedPlayer = session.getPlayer();
                // Find the newly added item by comparing item-id counts before and after
                java.util.Map<io.taanielo.jmud.core.world.ItemId, Long> oldCounts =
                    playerBeforeGet.getInventory().stream()
                        .collect(java.util.stream.Collectors.groupingBy(Item::getId, java.util.stream.Collectors.counting()));
                QuestDeliveryService deliverySvc = new QuestDeliveryService(context.questRepository());
                for (Item item : updatedPlayer.getInventory()) {
                    long before = oldCounts.getOrDefault(item.getId(), 0L);
                    long after = updatedPlayer.getInventory().stream()
                        .filter(i -> i.getId().equals(item.getId())).count();
                    if (after > before) {
                        deliverySvc.checkPickupProgress(updatedPlayer, item.getId())
                            .ifPresent(connection::writeLine);
                        break;
                    }
                }
            }
            sendPrompt();
        }

        @Override
        public void dropItem(String args) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to drop items.");
                return;
            }
            cancelRestIfActive();
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
            cancelRestIfActive();
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
            cancelRestIfActive();
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
            cancelRestIfActive();
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
            cancelRestIfActive();
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
            // Skip the sender — they already see "You gossip: ..." from GossipCommand.
            context.messageBroadcaster().broadcastGlobal(
                new PlainTextMessage(senderName + " gossips: " + message),
                Set.of(Username.of(senderName))
            );
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

        @Override
        public void considerMob(String args) {
            Player player = session.getPlayer();
            if (!session.isAuthenticated() || player == null) {
                writeLineWithPrompt("You must be logged in to consider a target.");
                return;
            }
            if (args == null || args.isBlank()) {
                writeLineWithPrompt("Consider whom?");
                return;
            }
            if (context.mobRegistry() == null) {
                writeLineWithPrompt("There is no " + args.trim() + " here to consider.");
                return;
            }
            var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
            if (roomIdOpt.isEmpty()) {
                writeLineWithPrompt("You are nowhere.");
                return;
            }
            java.util.List<io.taanielo.jmud.core.mob.MobInstance> mobs =
                context.mobRegistry().getMobsInRoom(roomIdOpt.get());
            String normalized = args.trim().toLowerCase(java.util.Locale.ROOT);
            io.taanielo.jmud.core.mob.MobInstance found = null;
            for (io.taanielo.jmud.core.mob.MobInstance mob : mobs) {
                String name = mob.template().name().toLowerCase(java.util.Locale.ROOT);
                if (name.equals(normalized) || name.startsWith(normalized)) {
                    found = mob;
                    break;
                }
            }
            if (found == null) {
                writeLineWithPrompt("There is no " + args.trim() + " here to consider.");
                return;
            }
            int mobMaxHp = found.template().maxHp();
            int playerMaxHp = player.getVitals().maxHp();
            double ratio = playerMaxHp > 0 ? (double) mobMaxHp / playerMaxHp : Double.MAX_VALUE;
            String tier;
            if (ratio <= 0.25) {
                tier = "poses no real threat";
            } else if (ratio <= 0.60) {
                tier = "looks like an easy opponent";
            } else if (ratio <= 1.00) {
                tier = "seems like a fair fight";
            } else if (ratio <= 1.50) {
                tier = "would be a serious challenge";
            } else {
                tier = "could kill you without effort";
            }
            writeLineWithPrompt("The " + found.template().name() + " " + tier + ".");
        }

        @Override
        public void startResting() {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to rest.");
                return;
            }
            Player player = session.getPlayer();
            if (player.isDead()) {
                writeLineWithPrompt("You cannot rest while dead.");
                return;
            }
            if (player.isResting()) {
                writeLineWithPrompt("You are already resting.");
                return;
            }
            if (context.mobRegistry() != null
                    && context.mobRegistry().isInCombat(player.getUsername())) {
                writeLineWithPrompt("You cannot rest while in combat.");
                return;
            }
            Player resting = player.withResting(true);
            session.setPlayer(resting);
            connection.writeLine("You sit down and begin to rest.");
            sendPrompt();
            RestingTicker ticker = new RestingTicker(
                session::getPlayer,
                this::applyRestUpdate,
                (msg, woken) -> {
                    session.setPlayer(woken);
                    session.clearRestingTicker();
                    connection.writeLine(msg);
                    sendPrompt();
                },
                RestSettings.regenHp(),
                RestSettings.regenMana(),
                RestSettings.regenMove()
            );
            session.registerRestingTicker(ticker);
        }

        @Override
        public void stopResting(String message) {
            Player player = session.getPlayer();
            if (player == null || !player.isResting()) {
                if (message != null && !message.isBlank()) {
                    writeLineWithPrompt(message);
                } else {
                    sendPrompt();
                }
                return;
            }
            Player woken = player.withResting(false);
            session.setPlayer(woken);
            session.clearRestingTicker();
            if (message != null && !message.isBlank()) {
                writeLineWithPrompt(message);
            } else {
                sendPrompt();
            }
        }

        @Override
        public void listShopInventory() {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to browse a shop.");
                return;
            }
            if (context.shopService() == null) {
                writeLineWithPrompt("There is no shop here.");
                return;
            }
            Player player = session.getPlayer();
            var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
            if (roomIdOpt.isEmpty()) {
                writeLineWithPrompt("You are nowhere.");
                return;
            }
            var shopOpt = context.shopService().findShopInRoom(roomIdOpt.get());
            if (shopOpt.isEmpty()) {
                writeLineWithPrompt("There is no shop here.");
                return;
            }
            for (String line : context.shopService().formatListing(shopOpt.get())) {
                connection.writeLine(line);
            }
            sendPrompt();
        }

        @Override
        public void buyFromShop(String args) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to buy items.");
                return;
            }
            if (context.shopService() == null) {
                writeLineWithPrompt("There is no shop here.");
                return;
            }
            Player player = session.getPlayer();
            var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
            if (roomIdOpt.isEmpty()) {
                writeLineWithPrompt("You are nowhere.");
                return;
            }
            var shopOpt = context.shopService().findShopInRoom(roomIdOpt.get());
            if (shopOpt.isEmpty()) {
                writeLineWithPrompt("There is no shop here.");
                return;
            }
            ShopTransactionResult result = context.shopService().buy(player, shopOpt.get(), args);
            if (result.success()) {
                session.replacePlayer(result.updatedPlayer());
            }
            writeLineWithPrompt(result.message());
        }

        @Override
        public void sellToShop(String args) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to sell items.");
                return;
            }
            if (context.shopService() == null) {
                writeLineWithPrompt("There is no shop here.");
                return;
            }
            Player player = session.getPlayer();
            var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
            if (roomIdOpt.isEmpty()) {
                writeLineWithPrompt("You are nowhere.");
                return;
            }
            var shopOpt = context.shopService().findShopInRoom(roomIdOpt.get());
            if (shopOpt.isEmpty()) {
                writeLineWithPrompt("There is no shop here.");
                return;
            }
            ShopTransactionResult result = context.shopService().sell(player, shopOpt.get(), args);
            if (result.success()) {
                session.replacePlayer(result.updatedPlayer());
            }
            writeLineWithPrompt(result.message());
        }

        @Override
        public void executeQuest(String args) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to manage quests.");
                return;
            }
            QuestRepository questRepo = context.questRepository();
            if (questRepo == null) {
                writeLineWithPrompt("Quests are not available.");
                return;
            }

            String[] parts = args == null ? new String[]{"", ""} : SocketCommandParsing.splitInput(args);
            String sub = parts[0]; // already uppercased by splitInput
            String subArgs = parts[1];

            switch (sub) {
                case "LIST" -> handleQuestList(questRepo);
                case "ACCEPT" -> handleQuestAccept(questRepo, subArgs);
                case "STATUS" -> handleQuestStatus(questRepo);
                case "COMPLETE" -> handleQuestComplete(questRepo);
                case "DELIVER" -> handleQuestDeliver(questRepo);
                case "ABANDON" -> handleQuestAbandon();
                default -> writeLineWithPrompt(
                    "Usage: QUEST [LIST|ACCEPT <id>|STATUS|COMPLETE|DELIVER|ABANDON]");
            }
        }

        @Override
        public void executeTrain(String args) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to train.");
                return;
            }
            Player player = session.getPlayer();
            var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
            if (roomIdOpt.isEmpty() || !"training-yard".equals(roomIdOpt.get().getValue())) {
                writeLineWithPrompt("The Master Trainer is not here. Find them in the Training Yard.");
                return;
            }
            // Verify trainer NPC is present in the room
            if (context.mobRegistry() != null) {
                var mobs = context.mobRegistry().getMobsInRoom(roomIdOpt.get());
                boolean trainerPresent = mobs.stream()
                    .anyMatch(m -> "trainer".equals(m.template().id().getValue()));
                if (!trainerPresent) {
                    writeLineWithPrompt("The Master Trainer is not here.");
                    return;
                }
            }
            String[] parts = args == null ? new String[]{"", ""} : SocketCommandParsing.splitInput(args);
            String sub = parts[0];
            String subArgs = parts[1];
            switch (sub) {
                case "LIST" -> handleTrainList(player);
                case "" -> writeLineWithPrompt("Usage: TRAIN LIST  or  TRAIN <ability-id>");
                default -> handleTrainAbility(player, sub + (subArgs.isBlank() ? "" : " " + subArgs));
            }
        }

        @Override
        public void executeParty(String args) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to use party commands.");
                return;
            }
            PartyService partyService = context.partyService();
            if (partyService == null) {
                writeLineWithPrompt("The party system is not available.");
                return;
            }
            Player player = session.getPlayer();
            String[] parts = args == null ? new String[]{"", ""} : SocketCommandParsing.splitInput(args);
            String sub = parts[0];
            String subArgs = parts[1];

            switch (sub) {
                case "FORM" -> handlePartyForm(player, partyService);
                case "INVITE" -> handlePartyInvite(player, partyService, subArgs);
                case "ACCEPT" -> handlePartyAccept(player, partyService);
                case "DECLINE" -> handlePartyDecline(player, partyService);
                case "LEAVE" -> handlePartyLeave(player, partyService);
                case "DISBAND" -> handlePartyDisband(player, partyService);
                case "" -> handlePartyStatus(player, partyService);
                default -> writeLineWithPrompt(
                    "Usage: PARTY [FORM|INVITE <player>|ACCEPT|DECLINE|LEAVE|DISBAND]");
            }
        }

        @Override
        public void lockExit(Direction direction) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to lock doors.");
                return;
            }
            Player player = session.getPlayer();
            RoomService.DoorActionResult result = roomService.lock(
                player.getUsername(), direction, player.getInventory());
            if (result.roomMessage() != null) {
                deliverRoomMessage(player.getUsername(), null, result.roomMessage());
            }
            writeLineWithPrompt(result.playerMessage());
        }

        @Override
        public void unlockExit(Direction direction) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to unlock doors.");
                return;
            }
            Player player = session.getPlayer();
            RoomService.DoorActionResult result = roomService.unlock(
                player.getUsername(), direction, player.getInventory());
            if (result.roomMessage() != null) {
                deliverRoomMessage(player.getUsername(), null, result.roomMessage());
            }
            writeLineWithPrompt(result.playerMessage());
        }

        @Override
        public void depositToBank(String args) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to use the bank.");
                return;
            }
            if (context.bankService() == null) {
                writeLineWithPrompt("There is no bank here.");
                return;
            }
            Player player = session.getPlayer();
            var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
            if (roomIdOpt.isEmpty()) {
                writeLineWithPrompt("You are nowhere.");
                return;
            }
            if (context.bankService().findBankInRoom(roomIdOpt.get()).isEmpty()) {
                writeLineWithPrompt("There is no bank here.");
                return;
            }
            if (args == null || args.isBlank()) {
                writeLineWithPrompt("Deposit how much gold? Usage: DEPOSIT <amount>");
                return;
            }
            int amount;
            try {
                amount = Integer.parseInt(args.trim());
            } catch (NumberFormatException e) {
                writeLineWithPrompt("'" + args.trim() + "' is not a valid amount.");
                return;
            }
            BankTransactionResult result = context.bankService().deposit(player, amount);
            if (result.success()) {
                session.replacePlayer(result.updatedPlayer());
            }
            writeLineWithPrompt(result.message());
        }

        @Override
        public void withdrawFromBank(String args) {
            if (!session.isAuthenticated() || session.getPlayer() == null) {
                writeLineWithPrompt("You must be logged in to use the bank.");
                return;
            }
            if (context.bankService() == null) {
                writeLineWithPrompt("There is no bank here.");
                return;
            }
            Player player = session.getPlayer();
            var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
            if (roomIdOpt.isEmpty()) {
                writeLineWithPrompt("You are nowhere.");
                return;
            }
            if (context.bankService().findBankInRoom(roomIdOpt.get()).isEmpty()) {
                writeLineWithPrompt("There is no bank here.");
                return;
            }
            if (args == null || args.isBlank()) {
                writeLineWithPrompt("Withdraw how much gold? Usage: WITHDRAW <amount>");
                return;
            }
            int amount;
            try {
                amount = Integer.parseInt(args.trim());
            } catch (NumberFormatException e) {
                writeLineWithPrompt("'" + args.trim() + "' is not a valid amount.");
                return;
            }
            BankTransactionResult result = context.bankService().withdraw(player, amount);
            if (result.success()) {
                session.replacePlayer(result.updatedPlayer());
            }
            writeLineWithPrompt(result.message());
        }

        private void handlePartyForm(Player player, PartyService partyService) {
            PartyService.PartyResult result = partyService.form(player.getUsername());
            writeLineWithPrompt(result.message());
        }

        private void handlePartyInvite(Player player, PartyService partyService, String targetName) {
            if (targetName == null || targetName.isBlank()) {
                writeLineWithPrompt("Invite whom? Usage: PARTY INVITE <player>");
                return;
            }
            Username invitee = Username.of(targetName.trim());
            List<Username> online = onlinePlayerNames();
            boolean inviteeOnline = online.contains(invitee);
            PartyService.PartyResult result = partyService.invite(
                player.getUsername(), invitee, inviteeOnline);
            writeLineWithPrompt(result.message());
            if (result.success()) {
                // Notify the invitee
                sendToUsername(invitee,
                    player.getUsername().getValue()
                        + " invites you to join their party. Type PARTY ACCEPT or PARTY DECLINE.");
            }
        }

        private void handlePartyAccept(Player player, PartyService partyService) {
            // Notify the inviter before accepting (so we can look up party members)
            java.util.Optional<Username> inviterOpt = partyService.getPendingInviter(player.getUsername());
            PartyService.PartyResult result = partyService.accept(player.getUsername());
            writeLineWithPrompt(result.message());
            if (result.success()) {
                inviterOpt.ifPresent(inviter ->
                    sendToUsername(inviter,
                        player.getUsername().getValue() + " has joined the party."));
            }
        }

        private void handlePartyDecline(Player player, PartyService partyService) {
            java.util.Optional<Username> inviterOpt = partyService.getPendingInviter(player.getUsername());
            PartyService.PartyResult result = partyService.decline(player.getUsername());
            writeLineWithPrompt(result.message());
            if (result.success()) {
                inviterOpt.ifPresent(inviter ->
                    sendToUsername(inviter,
                        player.getUsername().getValue() + " declined the party invitation."));
            }
        }

        private void handlePartyLeave(Player player, PartyService partyService) {
            List<Username> others = partyService.getOtherMembers(player.getUsername());
            PartyService.PartyResult result = partyService.leave(player.getUsername());
            writeLineWithPrompt(result.message());
            if (result.success()) {
                for (Username other : others) {
                    sendToUsername(other,
                        player.getUsername().getValue() + " has left the party.");
                }
            }
        }

        private void handlePartyDisband(Player player, PartyService partyService) {
            List<Username> others = partyService.getOtherMembers(player.getUsername());
            PartyService.PartyResult result = partyService.disband(player.getUsername());
            writeLineWithPrompt(result.message());
            if (result.success()) {
                for (Username other : others) {
                    sendToUsername(other, "The party has been disbanded by the leader.");
                }
            }
        }

        private void handlePartyStatus(Player player, PartyService partyService) {
            java.util.Optional<Party> partyOpt = partyService.findParty(player.getUsername());
            if (partyOpt.isEmpty()) {
                writeLineWithPrompt("You are not in a party. Use PARTY FORM to create one.");
                return;
            }
            Party party = partyOpt.get();
            connection.writeLine("Party members:");
            for (Username memberId : party.memberIds()) {
                String hp = findMemberCurrentHp(memberId);
                String leaderTag = party.isLeader(memberId) ? " (leader)" : "";
                connection.writeLine("  " + memberId.getValue() + leaderTag + "  HP: " + hp);
            }
            sendPrompt();
        }

        private void handleTrainList(Player player) {
            if (player.getClassId() == null) {
                writeLineWithPrompt("You have no class. Complete character creation first.");
                return;
            }
            CharacterCreationService ccs = context.characterCreationService();
            if (ccs == null) {
                writeLineWithPrompt("Trainer is unavailable.");
                return;
            }
            ClassDefinition classDef;
            try {
                classDef = ccs.resolveClassDefinition(player.getClassId().getValue()).orElse(null);
            } catch (CharacterCreationException e) {
                log.warn("Failed to load class definition for train list: {}", e.getMessage());
                writeLineWithPrompt("Trainer is unavailable.");
                return;
            }
            if (classDef == null || classDef.startingAbilityIds().isEmpty()) {
                writeLineWithPrompt("No trainable abilities found for your class.");
                return;
            }
            connection.writeLine("Master Trainer — Trainable Abilities (Practice Points: " + player.getPracticePoints() + "):");
            connection.writeLine(String.format("  %-24s %-8s %s", "Ability ID", "Cost", "Status"));
            connection.writeLine("  " + "-".repeat(48));
            for (AbilityId abilityId : classDef.startingAbilityIds()) {
                Ability ability = abilityRegistry.findById(abilityId).orElse(null);
                String displayId = abilityId.getValue();
                String name = ability != null ? ability.name() : displayId;
                boolean learned = player.getLearnedAbilities().contains(abilityId);
                String status = learned ? "learned" : "unlearned";
                connection.writeLine(String.format("  %-24s %-8s %s", displayId, "1 prac", status));
            }
            sendPrompt();
        }

        private void handleTrainAbility(Player player, String abilityInput) {
            if (player.getClassId() == null) {
                writeLineWithPrompt("You have no class. Complete character creation first.");
                return;
            }
            CharacterCreationService ccs = context.characterCreationService();
            if (ccs == null) {
                writeLineWithPrompt("Trainer is unavailable.");
                return;
            }
            ClassDefinition classDef;
            try {
                classDef = ccs.resolveClassDefinition(player.getClassId().getValue()).orElse(null);
            } catch (CharacterCreationException e) {
                log.warn("Failed to load class definition for train: {}", e.getMessage());
                writeLineWithPrompt("Trainer is unavailable.");
                return;
            }
            if (classDef == null) {
                writeLineWithPrompt("Trainer is unavailable.");
                return;
            }
            // Find the ability by id (case-insensitive match within class abilities)
            String normalized = abilityInput.trim().toLowerCase(java.util.Locale.ROOT);
            AbilityId targetId = classDef.startingAbilityIds().stream()
                .filter(id -> id.getValue().equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(null);
            if (targetId == null) {
                writeLineWithPrompt("'" + abilityInput.trim() + "' is not trainable by your class. Use TRAIN LIST to see options.");
                return;
            }
            if (player.getLearnedAbilities().contains(targetId)) {
                writeLineWithPrompt("You have already learned " + targetId.getValue() + ".");
                return;
            }
            if (player.getPracticePoints() <= 0) {
                writeLineWithPrompt("You have no practice points. Practice points are earned by levelling up.");
                return;
            }
            // Deduct one practice point, add the ability, and persist
            java.util.List<AbilityId> newAbilities = new java.util.ArrayList<>(player.getLearnedAbilities());
            newAbilities.add(targetId);
            Player updated = player
                .withPracticePoints(player.getPracticePoints() - 1)
                .withLearnedAbilities(newAbilities);
            session.replacePlayer(updated);
            saveOrWarn(updated);
            Ability ability = abilityRegistry.findById(targetId).orElse(null);
            String abilityName = ability != null ? ability.name() : targetId.getValue();
            writeLineWithPrompt("You have learned " + abilityName + "! (" + updated.getPracticePoints() + " practice point(s) remaining)");
        }

        private void handleQuestList(QuestRepository questRepo) {
            List<QuestTemplate> templates;
            try {
                templates = questRepo.findAll();
            } catch (QuestRepositoryException e) {
                log.warn("Failed to load quest list: {}", e.getMessage());
                writeLineWithPrompt("Quest list unavailable.");
                return;
            }
            if (templates.isEmpty()) {
                writeLineWithPrompt("There are no contracts available.");
                return;
            }
            connection.writeLine("Guild Clerk — Available Contracts:");
            connection.writeLine(String.format("  %-20s %-36s %s", "ID", "Description", "Reward"));
            connection.writeLine("  " + "-".repeat(72));
            for (QuestTemplate t : templates) {
                String desc = t.description().length() > 35
                    ? t.description().substring(0, 32) + "..."
                    : t.description();
                connection.writeLine(String.format(
                    "  %-20s %-36s %dg / %dxp",
                    t.id().getValue(), desc, t.goldReward(), t.xpReward()));
            }
            sendPrompt();
        }

        private void handleQuestAccept(QuestRepository questRepo, String questIdInput) {
            if (questIdInput == null || questIdInput.isBlank()) {
                writeLineWithPrompt("Accept which contract? Use QUEST ACCEPT <id>.");
                return;
            }
            Player player = session.getPlayer();
            if (player.getActiveQuest() != null) {
                writeLineWithPrompt("You already hold an active contract. QUEST ABANDON it first.");
                return;
            }
            QuestTemplate template;
            try {
                String normalized = questIdInput.trim().toLowerCase(Locale.ROOT);
                template = questRepo.findAll().stream()
                    .filter(t -> t.id().getValue().equalsIgnoreCase(normalized)
                        || t.name().equalsIgnoreCase(normalized))
                    .findFirst()
                    .orElse(null);
            } catch (QuestRepositoryException e) {
                log.warn("Failed to look up quest {}: {}", questIdInput, e.getMessage());
                writeLineWithPrompt("Quest lookup failed. Try again.");
                return;
            }
            if (template == null) {
                writeLineWithPrompt("Unknown contract '" + questIdInput.trim() + "'. Use QUEST LIST to see available contracts.");
                return;
            }
            ActiveQuest active = new ActiveQuest(template.id(), template.requiredKills());
            Player updated = player.withActiveQuest(active);
            session.replacePlayer(updated);
            if (template.isDeliveryQuest()) {
                writeLineWithPrompt(
                    "Contract accepted: " + template.name() + ". "
                        + "Collect " + template.requiredDropCount() + " "
                        + template.dropItemId() + "(s) and QUEST DELIVER them here. Good luck.");
            } else {
                writeLineWithPrompt(
                    "Contract accepted: " + template.name() + ". "
                        + "Kill " + template.requiredKills() + " "
                        + template.targetMobId() + "(s). Good luck.");
            }
        }

        private void handleQuestStatus(QuestRepository questRepo) {
            Player player = session.getPlayer();
            ActiveQuest active = player.getActiveQuest();
            if (active == null) {
                writeLineWithPrompt("No active contract.");
                return;
            }
            QuestTemplate template;
            try {
                template = questRepo.findById(active.templateId()).orElse(null);
            } catch (QuestRepositoryException e) {
                log.warn("Failed to load quest template {}: {}", active.templateId(), e.getMessage());
                writeLineWithPrompt("Quest status unavailable.");
                return;
            }
            if (template == null) {
                writeLineWithPrompt("Unknown quest. Use QUEST ABANDON to clear it.");
                return;
            }
            if (template.isDeliveryQuest()) {
                int held = 0;
                for (Item it : player.getInventory()) {
                    if (it.getId().getValue().equalsIgnoreCase(template.dropItemId())) {
                        held++;
                    }
                }
                if (held >= template.requiredDropCount()) {
                    writeLineWithPrompt(template.name() + ": " + held + "/" + template.requiredDropCount()
                        + " collected — return to the Guild Clerk and use QUEST DELIVER to claim your reward.");
                } else {
                    writeLineWithPrompt(template.name() + ": " + held + "/" + template.requiredDropCount() + " collected.");
                }
            } else if (active.isComplete()) {
                writeLineWithPrompt(template.name() + ": complete — return to the Guild Clerk to claim your reward.");
            } else {
                int done = template.requiredKills() - active.killsRemaining();
                writeLineWithPrompt(template.name() + ": " + done + "/" + template.requiredKills() + " kills.");
            }
        }

        private void handleQuestComplete(QuestRepository questRepo) {
            Player player = session.getPlayer();
            var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
            if (roomIdOpt.isEmpty()) {
                writeLineWithPrompt("You are nowhere.");
                return;
            }
            if (!"courtyard".equals(roomIdOpt.get().getValue())) {
                writeLineWithPrompt("The Guild Clerk is not here. Find them in the Courtyard.");
                return;
            }
            ActiveQuest active = player.getActiveQuest();
            if (active == null) {
                writeLineWithPrompt("You have no active contract to complete.");
                return;
            }
            // Delivery quests use QUEST DELIVER, not QUEST COMPLETE
            QuestTemplate templateCheck;
            try {
                templateCheck = questRepo.findById(active.templateId()).orElse(null);
            } catch (QuestRepositoryException e) {
                writeLineWithPrompt("Quest lookup failed.");
                return;
            }
            if (templateCheck != null && templateCheck.isDeliveryQuest()) {
                writeLineWithPrompt("This contract requires item delivery. Use QUEST DELIVER instead.");
                return;
            }
            if (!active.isComplete()) {
                QuestTemplate template;
                try {
                    template = questRepo.findById(active.templateId()).orElse(null);
                } catch (QuestRepositoryException e) {
                    writeLineWithPrompt("Quest lookup failed.");
                    return;
                }
                String name = template != null ? template.name() : active.templateId().getValue();
                writeLineWithPrompt("Contract not yet fulfilled: " + name + " (" + active.killsRemaining() + " kills remaining).");
                return;
            }
            QuestTemplate template;
            try {
                template = questRepo.findById(active.templateId()).orElse(null);
            } catch (QuestRepositoryException e) {
                log.warn("Failed to load quest template on complete: {}", e.getMessage());
                writeLineWithPrompt("Quest reward lookup failed.");
                return;
            }
            if (template == null) {
                writeLineWithPrompt("Unknown quest template. Contract cleared.");
                session.replacePlayer(player.withActiveQuest(null));
                return;
            }
            Player rewarded = player
                .withActiveQuest(null)
                .addGold(template.goldReward());
            // Award XP via LevelUpService to trigger any level-up logic
            io.taanielo.jmud.core.player.LevelUpService levelUpSvc = new io.taanielo.jmud.core.player.LevelUpService();
            io.taanielo.jmud.core.player.LevelUpService.LevelUpResult lvResult = levelUpSvc.awardXp(rewarded, template.xpReward());
            rewarded = lvResult.player();
            session.replacePlayer(rewarded);
            connection.writeLine(
                "The Guild Clerk nods approvingly. Contract complete: " + template.name() + ".");
            connection.writeLine(
                "You receive " + template.goldReward() + " gold and " + template.xpReward() + " experience.");
            if (lvResult.leveledUp()) {
                connection.writeLine("You have advanced to level " + rewarded.getLevel() + "!");
            }
            sendPrompt();
        }

        private void handleQuestDeliver(QuestRepository questRepo) {
            Player player = session.getPlayer();
            var roomIdOpt = roomService.findPlayerLocation(player.getUsername());
            if (roomIdOpt.isEmpty()) {
                writeLineWithPrompt("You are nowhere.");
                return;
            }
            if (!"courtyard".equals(roomIdOpt.get().getValue())) {
                writeLineWithPrompt("The Guild Clerk is not here. Find them in the Courtyard.");
                return;
            }
            QuestDeliveryService deliverySvc = new QuestDeliveryService(questRepo);
            QuestDeliveryService.DeliverResult result = deliverySvc.deliver(player);
            if (result.success()) {
                session.replacePlayer(result.player());
            }
            for (String msg : result.messages()) {
                connection.writeLine(msg);
            }
            sendPrompt();
        }

        private void handleQuestAbandon() {
            Player player = session.getPlayer();
            if (player.getActiveQuest() == null) {
                writeLineWithPrompt("You have no active contract to abandon.");
                return;
            }
            session.replacePlayer(player.withActiveQuest(null));
            writeLineWithPrompt("Contract abandoned. No reward will be granted.");
        }

        /** Called by the resting ticker to apply regenerated vitals. */
        private void applyRestUpdate(Player updated) {
            session.setPlayer(updated);
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
