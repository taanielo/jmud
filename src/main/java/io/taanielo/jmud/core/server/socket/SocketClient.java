package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.ability.AbilityCooldownTracker;
import io.taanielo.jmud.core.ability.AbilityCostResolver;
import io.taanielo.jmud.core.ability.AbilityEffect;
import io.taanielo.jmud.core.ability.AbilityEffectKind;
import io.taanielo.jmud.core.ability.AbilityEffectListener;
import io.taanielo.jmud.core.ability.AbilityEngine;
import io.taanielo.jmud.core.ability.AbilityMatch;
import io.taanielo.jmud.core.ability.AbilityMessageSink;
import io.taanielo.jmud.core.ability.AbilityRegistry;
import io.taanielo.jmud.core.ability.AbilityTargetResolver;
import io.taanielo.jmud.core.ability.AbilityUseResult;
import io.taanielo.jmud.core.ability.BasicAbilityCostResolver;
import io.taanielo.jmud.core.ability.CooldownTracker;
import io.taanielo.jmud.core.ability.DefaultAbilityEffectResolver;
import io.taanielo.jmud.core.ability.RoomAbilityTargetResolver;
import io.taanielo.jmud.core.audit.AuditEvent;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.AuditSubject;
import io.taanielo.jmud.core.authentication.AuthenticationService;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.authentication.UserRegistry;
import io.taanielo.jmud.core.combat.CombatEngine;
import io.taanielo.jmud.core.combat.CombatModifierResolver;
import io.taanielo.jmud.core.combat.CombatResult;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.DefaultCombatRandom;
import io.taanielo.jmud.core.combat.repository.AttackRepositoryException;
import io.taanielo.jmud.core.combat.repository.json.JsonAttackRepository;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.MessageWriter;
import io.taanielo.jmud.core.messaging.UserSayMessage;
import io.taanielo.jmud.core.messaging.WelcomeMessage;
import io.taanielo.jmud.core.output.OutputStyleSettings;
import io.taanielo.jmud.core.output.TextStyler;
import io.taanielo.jmud.core.output.TextStylers;
import io.taanielo.jmud.core.player.DeathSettings;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.player.PlayerRespawnTicker;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectMessageSink;
import io.taanielo.jmud.core.effects.EffectRepositoryException;
import io.taanielo.jmud.core.effects.EffectSettings;
import io.taanielo.jmud.core.effects.PlayerEffectTicker;
import io.taanielo.jmud.core.effects.repository.json.JsonEffectRepository;
import io.taanielo.jmud.core.healing.HealingEngine;
import io.taanielo.jmud.core.healing.HealingBaseResolver;
import io.taanielo.jmud.core.healing.HealingSettings;
import io.taanielo.jmud.core.healing.PlayerHealingTicker;
import io.taanielo.jmud.core.character.repository.json.JsonRaceRepository;
import io.taanielo.jmud.core.character.repository.json.JsonClassRepository;
import io.taanielo.jmud.core.character.repository.ClassRepositoryException;
import io.taanielo.jmud.core.character.repository.RaceRepositoryException;
import io.taanielo.jmud.core.prompt.PromptRenderer;
import io.taanielo.jmud.core.prompt.PromptSettings;
import io.taanielo.jmud.core.tick.TickRegistry;
import io.taanielo.jmud.core.tick.TickSubscription;
import io.taanielo.jmud.core.tick.system.CooldownSystem;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemEffect;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomService;

@Slf4j
public class SocketClient implements Client {

    private static final AtomicInteger SESSION_COUNTER = new AtomicInteger();

    private final Socket clientSocket;
    private final MessageBroadcaster messageBroadcaster;
    private final MessageWriter messageWriter;
    private final AuthenticationService authenticationService;
    private final PlayerRepository playerRepository;
    private final RoomService roomService;
    private final ClientPool clientPool;
    private final TickRegistry tickRegistry;
    private final AbilityRegistry abilityRegistry;
    private final AuditService auditService;
    private final Object writeLock = new Object();
    private final PromptRenderer promptRenderer = new PromptRenderer();
    private TextStyler textStyler;
    private final AbilityTargetResolver abilityTargetResolver;
    private final CooldownSystem abilityCooldowns = new CooldownSystem();
    private final AbilityCooldownTracker cooldownTracker = new CooldownTracker(abilityCooldowns);
    private final AbilityCostResolver abilityCostResolver = new BasicAbilityCostResolver();
    private final AbilityEngine abilityEngine;
    private final CombatEngine combatEngine;
    private final AbilityEffectListener abilityEffectListener;
    private final AbilityMessageSink abilityMessageSink;
    private final PlayerRespawnTicker respawnTicker;
    private final SocketCommandRegistry commandRegistry = new SocketCommandRegistry();
    private final SocketCommandDispatcher commandDispatcher;
    private final ExecutorService sessionExecutor;
    private final AtomicReference<Thread> sessionThread = new AtomicReference<>();
    private final SocketClientTickable sessionTickable;
    private TickSubscription sessionTickSubscription;
    private final AtomicBoolean closing = new AtomicBoolean(false);

    private OutputStream output;
    private InputStream input;
    private volatile boolean connected;

    private volatile boolean authenticated;
    private volatile Player player;
    private boolean effectsInitialized;
    private boolean healingInitialized;
    private PlayerEffectTicker effectTicker;
    private PlayerHealingTicker healingTicker;
    private final ThreadLocal<String> currentCorrelationId = new ThreadLocal<>();
    private boolean quitRequested;

    public SocketClient(
        Socket clientSocket,
        MessageBroadcaster messageBroadcaster,
        UserRegistry userRegistry,
        PlayerRepository playerRepository,
        RoomService roomService,
        TickRegistry tickRegistry,
        ClientPool clientPool,
        AbilityRegistry abilityRegistry,
        AuditService auditService
    ) throws IOException {
        this.clientSocket = clientSocket;
        this.messageBroadcaster = messageBroadcaster;
        this.messageWriter = new SocketMessageWriter(clientSocket);
        this.authenticationService = new SocketAuthenticationService(clientSocket, userRegistry, messageWriter);
        this.playerRepository = playerRepository;
        this.roomService = roomService;
        this.clientPool = clientPool;
        this.tickRegistry = tickRegistry;
        this.abilityRegistry = abilityRegistry;
        this.auditService = Objects.requireNonNull(auditService, "Audit service is required");
        this.abilityTargetResolver = new RoomAbilityTargetResolver(roomService, playerRepository);
        this.abilityMessageSink = new SocketAbilityMessageSink();
        this.abilityEffectListener = new SocketAbilityEffectListener();
        this.abilityEngine = createAbilityEngine(abilityRegistry);
        this.combatEngine = createCombatEngine();
        this.respawnTicker = new PlayerRespawnTicker(() -> player, this::applyRespawnUpdate, roomService, DeathSettings.respawnTicks());
        this.commandDispatcher = new SocketCommandDispatcher(commandRegistry, auditService);
        int sessionId = SESSION_COUNTER.getAndIncrement();
        this.sessionExecutor = Executors.newSingleThreadExecutor(sessionThreadFactory(sessionId));
        this.sessionTickable = new SocketClientTickable(this);
        registerCommands();
        init();
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

    private AbilityEngine createAbilityEngine(AbilityRegistry registry) {
        try {
            EffectEngine engine = new EffectEngine(new JsonEffectRepository());
            DefaultAbilityEffectResolver resolver = new DefaultAbilityEffectResolver(
                engine,
                new NoOpEffectMessageSink(),
                abilityEffectListener
            );
            return new AbilityEngine(registry, abilityCostResolver, resolver, abilityMessageSink);
        } catch (EffectRepositoryException e) {
            throw new IllegalStateException("Failed to initialize ability effects: " + e.getMessage(), e);
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

    private void init() {
        // No command handler to register now
    }

    @Override
    public void run() {
        log.debug("Initializing connection ..");
        try {
            output = clientSocket.getOutputStream();
            input = clientSocket.getInputStream();
            connected = true;
        } catch (IOException e) {
            log.error("Error connecting client", e);
        }
        textStyler = TextStylers.forEnabled(OutputStyleSettings.ansiEnabledByDefault());
        sessionTickSubscription = tickRegistry.register(sessionTickable);
        int onlineCount = Math.max(0, clientPool.clients().size() - 1);
        sendMessage(WelcomeMessage.of(textStyler, onlineCount));
        authenticated = false;


        String clientInput;
        try {

            byte[] bytes = new byte[1024];
            int read;
            while (connected && (read = input.read(bytes)) != -1) {
                log.debug("Read: {}", read);
                if (SocketCommand.isIAC(bytes)) {
                    if (SocketCommand.isIP(bytes)) {
                        log.debug("Received IP, closing connection ..");
                        break;
                    } else {
                        // ignore IAC responses for now
                        log.debug("Received IAC [{}], skipping ..", bytes);
                    }
                    continue;
                }
                clientInput = SocketCommand.readString(bytes);
                log.debug("Received: \"{}\" [{}]", clientInput, bytes);

                if (!authenticated) {
                    authenticationService.authenticate(clientInput, authenticatedUser -> runOnSessionThread(() -> {
                        authenticated = true;
                        AtomicBoolean isNewPlayer = new AtomicBoolean(false);
                        player = playerRepository
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
                        textStyler = TextStylers.forEnabled(player.isAnsiEnabled());
                        if (player.isDead()) {
                            roomService.clearPlayerLocation(player.getUsername());
                        } else {
                            roomService.ensurePlayerLocation(player.getUsername());
                        }
                        registerEffects();
                        registerHealing();
                        handleDeathState();
                        emitAudit(
                            "player.login",
                            AuditSubject.player(player.getUsername()),
                            null,
                            resolveRoomId(player),
                            "success",
                            Map.of("newPlayer", isNewPlayer.get())
                        );
                        String correlationId = auditService.newCorrelationId();
                        commandDispatcher.dispatch(new SocketCommandContextImpl(), "look", correlationId);
                    }));
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
        runOnSessionThreadAsync(() -> {
            if (!connected) {
                return;
            }
            try {
                synchronized (writeLock) {
                    message.send(messageWriter);
                }
            } catch (IOException e) {
                log.error("Error sending message", e);
            }
            sendPromptInternal();
        });
    }

    public void sendMessage(UserSayMessage message) {
        runOnSessionThreadAsync(() -> {
            if (!connected || player == null || message.getUsername().equals(player.getUsername())) {
                return;
            }
            try {
                synchronized (writeLock) {
                    message.send(messageWriter);
                }
            } catch (IOException e) {
                log.error("Error sending message", e);
            }
            sendPromptInternal();
        });
    }

    @Override
    public void close() {
        if (closing.compareAndSet(false, true)) {
            runOnSessionThread(this::closeInternal);
            shutdownExecutor();
        }
        try {
            input.close();
        } catch (IOException e) {
            log.error("Cannot close stream", e);
        }
        try {
            output.close();
        } catch (IOException e) {
            log.error("Cannot close stream", e);
        }
        try {
            if (clientSocket.isConnected()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            log.error("Error closing socket", e);
        }
        log.debug("Connection closed");
    }

    private void closeInternal() {
        log.debug("Closing connection ..");
        connected = false;
        clearEffects();
        clearHealing();
        if (sessionTickSubscription != null) {
            sessionTickSubscription.unsubscribe();
        }
        if (authenticated && player != null) {
            Map<String, Object> metadata = Map.of("reason", quitRequested ? "quit" : "disconnect");
            emitAudit(
                "player.logout",
                AuditSubject.player(player.getUsername()),
                null,
                resolveRoomId(player),
                "success",
                metadata
            );
            playerRepository.savePlayer(player);
            log.info("Player {} data saved on disconnect.", player.getUsername());
        }
    }

    private void handleCommand(String clientInput) {
        String correlationId = auditService.newCorrelationId();
        runOnSessionThread(() -> {
            currentCorrelationId.set(correlationId);
            try {
                commandDispatcher.dispatch(new SocketCommandContextImpl(), clientInput, correlationId);
            } finally {
                currentCorrelationId.remove();
            }
        });
    }

    void enqueueTick() {
        if (closing.get() || sessionExecutor.isShutdown()) {
            return;
        }
        submitAction(this::runTickWork);
    }

    private CompletableFuture<Void> submitAction(Runnable action) {
        Objects.requireNonNull(action, "Action is required");
        if (sessionExecutor.isShutdown()) {
            action.run();
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(action, sessionExecutor);
    }

    private void runOnSessionThread(Runnable action) {
        Objects.requireNonNull(action, "Action is required");
        if (isSessionThread() || sessionExecutor.isShutdown()) {
            action.run();
            return;
        }
        try {
            submitAction(action).join();
        } catch (CompletionException e) {
            throw unwrapCompletionException(e);
        }
    }

    private void runOnSessionThreadAsync(Runnable action) {
        Objects.requireNonNull(action, "Action is required");
        if (isSessionThread() || sessionExecutor.isShutdown()) {
            action.run();
            return;
        }
        submitAction(action);
    }

    private boolean isSessionThread() {
        return Thread.currentThread() == sessionThread.get();
    }

    private void shutdownExecutor() {
        sessionExecutor.shutdown();
    }

    private void runTickWork() {
        try {
            abilityCooldowns.tick();
        } catch (Exception e) {
            log.error("Failed to tick cooldowns", e);
        }
        try {
            respawnTicker.tick();
        } catch (Exception e) {
            log.error("Failed to tick respawn", e);
        }
        if (effectsInitialized && effectTicker != null) {
            try {
                effectTicker.tick();
            } catch (Exception e) {
                log.error("Failed to tick effects", e);
            }
        }
        if (healingInitialized && healingTicker != null) {
            try {
                healingTicker.tick();
            } catch (Exception e) {
                log.error("Failed to tick healing", e);
            }
        }
    }

    private ThreadFactory sessionThreadFactory(int sessionId) {
        return runnable -> {
            Thread thread = Thread.ofVirtual()
                .name("player-session-" + sessionId)
                .factory()
                .newThread(runnable);
            sessionThread.set(thread);
            return thread;
        };
    }

    private RuntimeException unwrapCompletionException(CompletionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new RuntimeException(cause);
    }

    private void writeLinesWithPrompt(List<String> lines) {
        runOnSessionThreadAsync(() -> {
            for (String line : lines) {
                writeLineInternal(line);
            }
            sendPromptInternal();
        });
    }

    private void writeLineWithPrompt(String message) {
        runOnSessionThreadAsync(() -> {
            writeLineInternal(message);
            sendPromptInternal();
        });
    }

    private void writeLineSafe(String message) {
        runOnSessionThreadAsync(() -> writeLineInternal(message));
    }

    private void writeLineInternal(String message) {
        synchronized (writeLock) {
            try {
                messageWriter.writeLine(message);
            } catch (IOException e) {
                log.error("Error writing message", e);
            }
        }
    }

    private void handleAnsiCommand(String args) {
        if (!authenticated || player == null) {
            writeLineWithPrompt("You must be logged in to change ANSI settings.");
            return;
        }
        String normalized = args == null ? "" : args.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.equals("STATUS")) {
            writeLineWithPrompt("ANSI is " + (player.isAnsiEnabled() ? "ON" : "OFF"));
            return;
        }
        switch (normalized) {
            case "ON":
                setAnsiEnabled(true);
                return;
            case "OFF":
                setAnsiEnabled(false);
                return;
            case "TOGGLE":
                setAnsiEnabled(!player.isAnsiEnabled());
                return;
            default:
                writeLineWithPrompt("Usage: ANSI [on|off|toggle|status]");
        }
    }

    private void setAnsiEnabled(boolean enabled) {
        if (player.isAnsiEnabled() == enabled) {
            writeLineWithPrompt("ANSI is already " + (enabled ? "ON" : "OFF"));
            return;
        }
        replacePlayer(player.withAnsiEnabled(enabled));
        textStyler = TextStylers.forEnabled(player.isAnsiEnabled());
        writeLineWithPrompt("ANSI is now " + (enabled ? "ON" : "OFF"));
    }

    private void handleAbilityCommand(String args) {
        if (!authenticated || player == null) {
            writeLineWithPrompt("You must be logged in to use abilities.");
            return;
        }
        AbilityMatch match = abilityRegistry.findBestMatch(args, player.getLearnedAbilities()).orElse(null);
        AbilityUseResult result = abilityEngine.use(
            player,
            args,
            player.getLearnedAbilities(),
            abilityTargetResolver,
            cooldownTracker
        );
        auditAbilityUse(match, result, args);
        Player updatedSource = result.source();
        Player updatedTarget = resolveDeathIfNeeded(result.target(), updatedSource);
        if (updatedTarget.getUsername().equals(updatedSource.getUsername())) {
            updatedSource = updatedTarget;
        }
        replacePlayer(updatedSource);
        if (!updatedTarget.getUsername().equals(player.getUsername())) {
            updateTarget(updatedTarget);
        }
        for (String message : result.messages()) {
            writeLineSafe(message);
        }
        sendPrompt();
    }

    private void handleAttackCommand(String args) {
        if (!authenticated || player == null) {
            writeLineWithPrompt("You must be logged in to attack.");
            return;
        }
        String normalized = args == null ? "" : args.trim();
        if (normalized.isEmpty()) {
            writeLineWithPrompt("Usage: attack <target>");
            return;
        }
        Optional<Player> targetMatch = resolveTarget(player, normalized);
        if (targetMatch.isEmpty()) {
            writeLineWithPrompt("No such target to attack.");
            return;
        }
        Player target = targetMatch.get();
        if (target.getUsername().equals(player.getUsername())) {
            writeLineWithPrompt("You cannot attack yourself.");
            return;
        }
        try {
            CombatResult result = combatEngine.resolve(player, target, CombatSettings.defaultAttackId());
            if (result.sourceMessage() != null && !result.sourceMessage().isBlank()) {
                writeLineSafe(result.sourceMessage());
            }
            if (result.targetMessage() != null && !result.targetMessage().isBlank()) {
                sendToUsername(target.getUsername(), result.targetMessage());
            }
            if (result.roomMessage() != null && !result.roomMessage().isBlank()) {
                sendToRoom(player, target, result.roomMessage());
            }
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("attackId", CombatSettings.defaultAttackId().getValue());
            metadata.put("hit", result.hit());
            metadata.put("crit", result.crit());
            metadata.put("damage", result.damage());
            emitAudit(
                "combat.attack",
                AuditSubject.player(player.getUsername()),
                AuditSubject.player(target.getUsername()),
                resolveRoomId(player),
                result.hit() ? "hit" : "miss",
                metadata
            );
            Player updatedTarget = resolveDeathIfNeeded(result.target(), player);
            if (!updatedTarget.getUsername().equals(player.getUsername())) {
                updateTarget(updatedTarget);
            }
        } catch (AttackRepositoryException | EffectRepositoryException e) {
            log.error("Failed to resolve attack", e);
            writeLineWithPrompt("Combat failed: " + e.getMessage());
            return;
        }
        sendPrompt();
    }

    private void handleGetCommand(String args) {
        if (!authenticated || player == null) {
            writeLineWithPrompt("You must be logged in to get items.");
            return;
        }
        String normalized = args == null ? "" : args.trim();
        if (normalized.isEmpty()) {
            writeLineWithPrompt("Get what?");
            return;
        }
        RoomService.LookResult look = roomService.look(player.getUsername());
        Room room = look.room();
        if (room == null) {
            writeLineWithPrompt("You cannot get items here.");
            return;
        }
        java.util.Optional<Item> item = roomService.takeItem(player.getUsername(), normalized);
        if (item.isEmpty()) {
            writeLineWithPrompt("You don't see that here.");
            return;
        }
        replacePlayer(player.addItem(item.get()));
        emitAudit(
            "item.get",
            AuditSubject.player(player.getUsername()),
            AuditSubject.item(item.get()),
            room == null ? null : room.getId().getValue(),
            "success",
            Map.of("itemName", item.get().getName())
        );
        writeLineWithPrompt("You pick up " + item.get().getName() + ".");
    }

    private void handleDropCommand(String args) {
        if (!authenticated || player == null) {
            writeLineWithPrompt("You must be logged in to drop items.");
            return;
        }
        String normalized = args == null ? "" : args.trim();
        if (normalized.isEmpty()) {
            writeLineWithPrompt("Drop what?");
            return;
        }
        Item item = findInventoryItem(normalized);
        if (item == null) {
            writeLineWithPrompt("You aren't carrying that.");
            return;
        }
        roomService.dropItem(player.getUsername(), item);
        replacePlayer(player.removeItem(item));
        emitAudit(
            "item.drop",
            AuditSubject.player(player.getUsername()),
            AuditSubject.item(item),
            resolveRoomId(player),
            "success",
            Map.of("itemName", item.getName())
        );
        writeLineWithPrompt("You drop " + item.getName() + ".");
    }

    private void handleQuaffCommand(String args) {
        if (!authenticated || player == null) {
            writeLineWithPrompt("You must be logged in to quaff.");
            return;
        }
        String normalized = args == null ? "" : args.trim();
        if (normalized.isEmpty()) {
            writeLineWithPrompt("Quaff what?");
            return;
        }
        Item item = findInventoryItem(normalized);
        if (item == null) {
            writeLineWithPrompt("You aren't carrying that.");
            return;
        }
        if (item.getEffects().isEmpty()) {
            writeLineWithPrompt("Nothing happens.");
            return;
        }
        try {
            EffectEngine engine = new EffectEngine(new JsonEffectRepository());
            for (ItemEffect effect : item.getEffects()) {
                boolean applied = engine.apply(player, effect.id(), new NoOpEffectMessageSink());
                if (applied) {
                    auditEffectApplied(effect.id().getValue(), "item", item.getId().getValue());
                }
            }
        } catch (EffectRepositoryException e) {
            writeLineWithPrompt("You cannot use that item right now.");
            return;
        }
        replacePlayer(player.removeItem(item));
        emitAudit(
            "item.quaff",
            AuditSubject.player(player.getUsername()),
            AuditSubject.item(item),
            resolveRoomId(player),
            "success",
            Map.of("itemName", item.getName())
        );
        writeLineWithPrompt("You quaff " + item.getName() + ".");
    }

    private void replacePlayer(Player updated) {
        player = updated;
        playerRepository.savePlayer(player);
        if (effectsInitialized) {
            clearEffects();
            if (!player.isDead()) {
                registerEffects();
            }
        }
        handleDeathState();
    }

    private void applyHealingUpdate(Player updated) {
        if (player != null) {
            int beforeHp = player.getVitals().hp();
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
                    AuditSubject.player(player.getUsername()),
                    null,
                    resolveRoomId(player),
                    "success",
                    metadata
                );
            }
        }
        if (updated.getVitals().hp() <= 0 && !updated.isDead()) {
            Player resolved = resolveDeathIfNeeded(updated, null);
            replacePlayer(resolved);
            return;
        }
        player = updated;
        playerRepository.savePlayer(player);
    }

    private void applyRespawnUpdate(Player updated) {
        replacePlayer(updated);
        writeLineSafe("You awaken in the starting room.");
        RoomService.LookResult result = roomService.look(player.getUsername());
        emitAudit(
            "player.respawn",
            AuditSubject.player(player.getUsername()),
            null,
            result.room() == null ? null : result.room().getId().getValue(),
            "success",
            Map.of()
        );
        writeLinesWithPrompt(result.lines());
    }

    private void handleDeathState() {
        if (player == null || !player.isDead()) {
            return;
        }
        if (respawnTicker.isScheduled()) {
            return;
        }
        abilityCooldowns.clear();
        respawnTicker.schedule();
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

    private Player resolveDeathIfNeeded(Player target, Player attacker) {
        Objects.requireNonNull(target, "Target is required");
        if (target.isDead() || target.getVitals().hp() > 0) {
            return target;
        }
        RoomService.LookResult look = roomService.look(target.getUsername());
        Room room = look.room();
        Player deadTarget = target.die();
        sendDeathMessages(attacker, deadTarget);
        AuditSubject actor = attacker == null
            ? AuditSubject.system("environment")
            : AuditSubject.player(attacker.getUsername());
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("cause", attacker == null ? "effect" : "combat");
        emitAudit(
            "player.death",
            actor,
            AuditSubject.player(deadTarget.getUsername()),
            room == null ? null : room.getId().getValue(),
            "success",
            metadata
        );
        if (room != null) {
            Item corpse = roomService.spawnCorpse(deadTarget.getUsername(), room.getId());
            emitAudit(
                "loot.corpse.spawned",
                AuditSubject.system("environment"),
                AuditSubject.item(corpse),
                room.getId().getValue(),
                "success",
                Map.of("owner", deadTarget.getUsername().getValue())
            );
        }
        roomService.clearPlayerLocation(deadTarget.getUsername());
        return deadTarget;
    }

    private void sendDeathMessages(Player attacker, Player target) {
        String targetName = target.getUsername().getValue();
        sendToUsername(target.getUsername(), "You have died.");
        if (attacker == null) {
            sendToRoom(target, target, targetName + " has died.");
            return;
        }
        if (!attacker.getUsername().equals(target.getUsername())) {
            sendToUsername(attacker.getUsername(), "You have slain " + targetName + ".");
        }
        String roomMessage = attacker.getUsername().equals(target.getUsername())
            ? targetName + " has died."
            : targetName + " has been slain by " + attacker.getUsername().getValue() + ".";
        sendToRoom(attacker, target, roomMessage);
    }

    private Item findInventoryItem(String input) {
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        for (Item item : player.getInventory()) {
            String name = item.getName().toLowerCase(Locale.ROOT);
            if (name.equals(normalized) || name.startsWith(normalized)) {
                return item;
            }
            String id = item.getId().getValue().toLowerCase(Locale.ROOT);
            if (id.equals(normalized) || id.startsWith(normalized)) {
                return item;
            }
        }
        return null;
    }

    private void auditAbilityUse(AbilityMatch match, AbilityUseResult result, String input) {
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
        metadata.put("messages", result.messages());
        AuditSubject target = result.target() == null
            ? null
            : AuditSubject.player(result.target().getUsername());
        emitAudit(
            "ability.use",
            AuditSubject.player(player.getUsername()),
            target,
            resolveRoomId(player),
            "attempted",
            metadata
        );
    }

    private void auditEffectApplied(String effectId, String originType, String originId) {
        if (player == null) {
            return;
        }
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("effectId", effectId);
        metadata.put("originType", originType);
        metadata.put("originId", originId);
        emitAudit(
            "effect.apply",
            AuditSubject.player(player.getUsername()),
            AuditSubject.player(player.getUsername()),
            resolveRoomId(player),
            "success",
            metadata
        );
    }

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

    private String resolveRoomId(Player player) {
        if (player == null) {
            return null;
        }
        return roomService.findPlayerLocation(player.getUsername())
            .map(roomId -> roomId.getValue())
            .orElse(null);
    }

    private boolean isAuthenticatedUser(Username username) {
        return authenticated && player != null && player.getUsername().equals(username);
    }

    private void applyExternalPlayerUpdate(Player updated) {
        runOnSessionThreadAsync(() -> {
            if (!isAuthenticatedUser(updated.getUsername())) {
                return;
            }
            replacePlayer(updated);
        });
    }

    private static class NoOpEffectMessageSink implements EffectMessageSink {
        @Override
        public void sendToTarget(String message) {
        }
    }

    private class SocketAbilityEffectListener implements AbilityEffectListener {
        @Override
        public void onApplied(AbilityEffect effect, io.taanielo.jmud.core.ability.AbilityContext context) {
            if (effect == null || context == null) {
                return;
            }
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("originType", "ability");
            metadata.put("kind", effect.kind().name().toLowerCase(Locale.ROOT));
            if (effect.kind() == AbilityEffectKind.EFFECT) {
                metadata.put("effectId", effect.effectId());
            } else {
                metadata.put("stat", effect.stat().name().toLowerCase(Locale.ROOT));
                metadata.put("operation", effect.operation().name().toLowerCase(Locale.ROOT));
                metadata.put("amount", effect.amount());
            }
            emitAudit(
                "effect.apply",
                AuditSubject.player(context.source().getUsername()),
                AuditSubject.player(context.target().getUsername()),
                resolveRoomId(context.source()),
                "success",
                metadata
            );
        }
    }

    private class SocketAbilityMessageSink implements AbilityMessageSink {
        @Override
        public void sendToSource(Player source, String message) {
            if (message == null || message.isBlank()) {
                return;
            }
            writeLineSafe(message);
        }

        @Override
        public void sendToTarget(Player target, String message) {
            if (message == null || message.isBlank()) {
                return;
            }
            sendToUsername(target.getUsername(), message);
        }

        @Override
        public void sendToRoom(Player source, Player target, String message) {
            if (message == null || message.isBlank()) {
                return;
            }
            RoomService.LookResult look = roomService.look(source.getUsername());
            Room room = look.room();
            if (room == null || room.getOccupants().isEmpty()) {
                return;
            }
            for (Username occupant : room.getOccupants()) {
                if (occupant.equals(source.getUsername()) || occupant.equals(target.getUsername())) {
                    continue;
                }
                SocketClient.this.sendToUsername(occupant, message);
            }
        }
    }

    private class SocketCommandContextImpl implements SocketCommandContext {
        @Override
        public boolean isAuthenticated() {
            return authenticated;
        }

        @Override
        public Player getPlayer() {
            return player;
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
            quitRequested = true;
            SocketClient.this.close();
        }

        @Override
        public void run() {
            SocketClient.this.run();
        }

        @Override
        public void sendLook() {
            if (!authenticated || player == null) {
                writeLineWithPrompt("You must be logged in to look around.");
                return;
            }
            RoomService.LookResult result = roomService.look(player.getUsername());
            writeLinesWithPrompt(result.lines());
        }

        @Override
        public void sendMove(io.taanielo.jmud.core.world.Direction direction) {
            if (!authenticated || player == null) {
                writeLineWithPrompt("You must be logged in to move.");
                return;
            }
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
            writeLinesWithPrompt(result.lines());
        }

        @Override
        public void useAbility(String args) {
            handleAbilityCommand(args);
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
            SocketClient.this.writeLineSafe(message);
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
            return SocketClient.this.resolveTarget(source, input);
        }

        @Override
        public void executeAttack(String args) {
            handleAttackCommand(args);
        }

        @Override
        public void getItem(String args) {
            handleGetCommand(args);
        }

        @Override
        public void dropItem(String args) {
            handleDropCommand(args);
        }

        @Override
        public void quaffItem(String args) {
            handleQuaffCommand(args);
        }

    }

    private Optional<Player> resolveTarget(Player source, String input) {
        return abilityTargetResolver.resolve(source, input);
    }

    private void sendToRoom(Player source, Player target, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        RoomService.LookResult look = roomService.look(source.getUsername());
        Room room = look.room();
        if (room == null || room.getOccupants().isEmpty()) {
            return;
        }
        for (Username occupant : room.getOccupants()) {
            if (occupant.equals(source.getUsername()) || occupant.equals(target.getUsername())) {
                continue;
            }
            sendToUsername(occupant, message);
        }
    }

    private void sendToUsername(Username username, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        for (Client client : clientPool.clients()) {
            if (client instanceof SocketClient socketClient) {
                if (socketClient.isAuthenticatedUser(username)) {
                    socketClient.writeLineSafe(message);
                    return;
                }
            }
        }
    }

    private void registerEffects() {
        if (!EffectSettings.enabled() || effectsInitialized || player == null || player.isDead()) {
            return;
        }
        try {
            EffectEngine engine = new EffectEngine(new JsonEffectRepository());
            EffectMessageSink sink = new EffectMessageSink() {
                @Override
                public void sendToTarget(String message) {
                    writeLineInternal(message);
                    sendPromptInternal();
                }
            };
            effectTicker = new PlayerEffectTicker(player, engine, sink);
        } catch (EffectRepositoryException e) {
            log.error("Failed to initialize effects", e);
            return;
        }
        effectsInitialized = true;
    }

    private void registerHealing() {
        if (!HealingSettings.enabled() || healingInitialized) {
            return;
        }
        try {
            HealingEngine engine = new HealingEngine(new JsonEffectRepository());
            HealingBaseResolver baseResolver = new HealingBaseResolver(new JsonRaceRepository(), new JsonClassRepository());
            healingTicker = new PlayerHealingTicker(() -> player, this::applyHealingUpdate, engine, baseResolver);
        } catch (EffectRepositoryException | RaceRepositoryException | ClassRepositoryException e) {
            log.error("Failed to initialize healing", e);
            return;
        }
        healingInitialized = true;
    }

    private void clearEffects() {
        effectTicker = null;
        effectsInitialized = false;
    }

    private void clearHealing() {
        healingTicker = null;
        healingInitialized = false;
    }

    private void sendPrompt() {
        runOnSessionThreadAsync(this::sendPromptInternal);
    }

    private void sendPromptInternal() {
        if (!authenticated || player == null) {
            return;
        }
        String format = player.getPromptFormat();
        if (format == null || format.isBlank()) {
            format = PromptSettings.defaultFormat();
        }
        String promptLine = promptRenderer.render(format, player);
        synchronized (writeLock) {
            try {
                messageWriter.write(promptLine + "> ");
            } catch (IOException e) {
                log.error("Error writing prompt", e);
            }
        }
    }
}
