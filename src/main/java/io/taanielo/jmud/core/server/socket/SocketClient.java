package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.ability.AbilityCooldownTracker;
import io.taanielo.jmud.core.ability.AbilityCostResolver;
import io.taanielo.jmud.core.ability.AbilityEngine;
import io.taanielo.jmud.core.ability.AbilityMessageSink;
import io.taanielo.jmud.core.ability.AbilityRegistry;
import io.taanielo.jmud.core.ability.AbilityTargetResolver;
import io.taanielo.jmud.core.ability.AbilityUseResult;
import io.taanielo.jmud.core.ability.BasicAbilityCostResolver;
import io.taanielo.jmud.core.ability.CooldownTracker;
import io.taanielo.jmud.core.ability.DefaultAbilityEffectResolver;
import io.taanielo.jmud.core.ability.RoomAbilityTargetResolver;
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
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
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
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomService;

@Slf4j
public class SocketClient implements Client {

    private final Socket clientSocket;
    private final MessageBroadcaster messageBroadcaster;
    private final MessageWriter messageWriter;
    private final AuthenticationService authenticationService;
    private final PlayerRepository playerRepository;
    private final RoomService roomService;
    private final ClientPool clientPool;
    private final TickRegistry tickRegistry;
    private final AbilityRegistry abilityRegistry;
    private final Object writeLock = new Object();
    private final PromptRenderer promptRenderer = new PromptRenderer();
    private TextStyler textStyler;
    private final AbilityTargetResolver abilityTargetResolver;
    private final CooldownSystem abilityCooldowns = new CooldownSystem();
    private final AbilityCooldownTracker cooldownTracker = new CooldownTracker(abilityCooldowns);
    private final AbilityCostResolver abilityCostResolver = new BasicAbilityCostResolver();
    private final AbilityEngine abilityEngine;
    private final CombatEngine combatEngine;
    private TickSubscription cooldownSubscription;
    private final AbilityMessageSink abilityMessageSink;
    private TickSubscription healingSubscription;
    private final SocketCommandRegistry commandRegistry = new SocketCommandRegistry();
    private final SocketCommandDispatcher commandDispatcher = new SocketCommandDispatcher(commandRegistry);

    private OutputStream output;
    private InputStream input;
    private boolean connected;

    private boolean authenticated;
    private Player player;
    private final List<TickSubscription> effectSubscriptions = new ArrayList<>();
    private boolean effectsInitialized;
    private boolean healingInitialized;

    public SocketClient(
        Socket clientSocket,
        MessageBroadcaster messageBroadcaster,
        UserRegistry userRegistry,
        PlayerRepository playerRepository,
        RoomService roomService,
        TickRegistry tickRegistry,
        ClientPool clientPool,
        AbilityRegistry abilityRegistry
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
        this.abilityTargetResolver = new RoomAbilityTargetResolver(roomService, playerRepository);
        this.abilityMessageSink = new SocketAbilityMessageSink();
        this.abilityEngine = createAbilityEngine(abilityRegistry);
        this.combatEngine = createCombatEngine();
        registerCommands();
        init();
    }

    private void registerCommands() {
        new LookCommand(commandRegistry);
        new MoveCommand(commandRegistry);
        new SayCommand(commandRegistry);
        new AbilityCommand(commandRegistry);
        new AttackCommand(commandRegistry);
        new AnsiCommand(commandRegistry);
        new QuitCommand(commandRegistry);
    }

    private AbilityEngine createAbilityEngine(AbilityRegistry registry) {
        try {
            EffectEngine engine = new EffectEngine(new JsonEffectRepository());
            DefaultAbilityEffectResolver resolver = new DefaultAbilityEffectResolver(engine, new NoOpEffectMessageSink());
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
        cooldownSubscription = tickRegistry.register(abilityCooldowns);
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
                    authenticationService.authenticate(clientInput, authenticatedUser -> {
                        authenticated = true;
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
                                playerRepository.savePlayer(newPlayer);
                                return newPlayer;
                            });
                        if (player.getLearnedAbilities().isEmpty()) {
                            player = player.withLearnedAbilities(abilityRegistry.abilityIds());
                            playerRepository.savePlayer(player);
                        }
                        textStyler = TextStylers.forEnabled(player.isAnsiEnabled());
                        roomService.ensurePlayerLocation(player.getUsername());
                        registerEffects();
                        registerHealing();
                        commandDispatcher.dispatch(new SocketCommandContextImpl(), "look");
                    });
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
            synchronized (writeLock) {
                message.send(messageWriter);
            }
        } catch (IOException e) {
            log.error("Error sending message", e);
        }
        sendPrompt();
    }

    public void sendMessage(UserSayMessage message) {
        if (message.getUsername().equals(player.getUsername())) {
            return;
        }
        try {
            synchronized (writeLock) {
                message.send(messageWriter);
            }
        } catch (IOException e) {
            log.error("Error sending message", e);
        }
        sendPrompt();
    }

    @Override
    public void close() {
        log.debug("Closing connection ..");
        connected = false;
        clearEffects();
        clearHealing();
        if (cooldownSubscription != null) {
            cooldownSubscription.unsubscribe();
        }
        if (authenticated && player != null) {
            playerRepository.savePlayer(player);
            log.info("Player {} data saved on disconnect.", player.getUsername());
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

    private void handleCommand(String clientInput) {
        commandDispatcher.dispatch(new SocketCommandContextImpl(), clientInput);
    }

    private void writeLinesWithPrompt(List<String> lines) {
        for (String line : lines) {
            writeLineSafe(line);
        }
        sendPrompt();
    }

    private void writeLineWithPrompt(String message) {
        writeLineSafe(message);
        sendPrompt();
    }

    private void writeLineSafe(String message) {
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
        AbilityUseResult result = abilityEngine.use(
            player,
            args,
            player.getLearnedAbilities(),
            abilityTargetResolver,
            cooldownTracker
        );
        replacePlayer(result.source());
        if (!result.target().getUsername().equals(player.getUsername())) {
            updateTarget(result.target());
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
            if (!result.target().getUsername().equals(player.getUsername())) {
                updateTarget(result.target());
            }
        } catch (AttackRepositoryException | EffectRepositoryException e) {
            log.error("Failed to resolve attack", e);
            writeLineWithPrompt("Combat failed: " + e.getMessage());
            return;
        }
        sendPrompt();
    }

    private void replacePlayer(Player updated) {
        player = updated;
        playerRepository.savePlayer(player);
        if (effectsInitialized) {
            clearEffects();
            registerEffects();
        }
    }

    private void applyHealingUpdate(Player updated) {
        player = updated;
        playerRepository.savePlayer(player);
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

    private boolean isAuthenticatedUser(Username username) {
        return authenticated && player != null && player.getUsername().equals(username);
    }

    private void applyExternalPlayerUpdate(Player updated) {
        if (!isAuthenticatedUser(updated.getUsername())) {
            return;
        }
        replacePlayer(updated);
    }

    private static class NoOpEffectMessageSink implements EffectMessageSink {
        @Override
        public void sendToTarget(String message) {
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
            RoomService.MoveResult result = roomService.move(player.getUsername(), direction);
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
        if (!EffectSettings.enabled() || effectsInitialized) {
            return;
        }
        try {
            EffectEngine engine = new EffectEngine(new JsonEffectRepository());
            EffectMessageSink sink = new EffectMessageSink() {
                @Override
                public void sendToTarget(String message) {
                    writeLineSafe(message);
                    sendPrompt();
                }
            };
            effectSubscriptions.add(tickRegistry.register(new PlayerEffectTicker(player, engine, sink)));
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
            healingSubscription = tickRegistry.register(
                new PlayerHealingTicker(() -> player, this::applyHealingUpdate, engine, baseResolver)
            );
        } catch (EffectRepositoryException | RaceRepositoryException | ClassRepositoryException e) {
            log.error("Failed to initialize healing", e);
            return;
        }
        healingInitialized = true;
    }

    private void clearEffects() {
        for (TickSubscription subscription : effectSubscriptions) {
            subscription.unsubscribe();
        }
        effectSubscriptions.clear();
        effectsInitialized = false;
    }

    private void clearHealing() {
        if (healingSubscription != null) {
            healingSubscription.unsubscribe();
            healingSubscription = null;
        }
        healingInitialized = false;
    }

    private void sendPrompt() {
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
