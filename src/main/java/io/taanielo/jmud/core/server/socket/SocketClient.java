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

import io.taanielo.jmud.command.CommandRegistry;
import io.taanielo.jmud.core.authentication.AuthenticationService;
import io.taanielo.jmud.core.authentication.UserRegistry;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.MessageWriter;
import io.taanielo.jmud.core.messaging.UserSayMessage;
import io.taanielo.jmud.core.messaging.WelcomeMessage;
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
import io.taanielo.jmud.core.prompt.PromptRenderer;
import io.taanielo.jmud.core.prompt.PromptSettings;
import io.taanielo.jmud.core.tick.TickRegistry;
import io.taanielo.jmud.core.tick.TickSubscription;
import io.taanielo.jmud.core.world.Direction;
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
    private final Object writeLock = new Object();
    private final PromptRenderer promptRenderer = new PromptRenderer();

    private OutputStream output;
    private InputStream input;
    private boolean connected;

    private boolean authenticated;
    private Player player;
    private final List<TickSubscription> effectSubscriptions = new ArrayList<>();
    private boolean effectsInitialized;

    public SocketClient(
        Socket clientSocket,
        MessageBroadcaster messageBroadcaster,
        UserRegistry userRegistry,
        PlayerRepository playerRepository,
        RoomService roomService,
        TickRegistry tickRegistry,
        ClientPool clientPool
    ) throws IOException {
        this.clientSocket = clientSocket;
        this.messageBroadcaster = messageBroadcaster;
        this.messageWriter = new SocketMessageWriter(clientSocket);
        this.authenticationService = new SocketAuthenticationService(clientSocket, userRegistry, messageWriter);
        this.playerRepository = playerRepository;
        this.roomService = roomService;
        this.clientPool = clientPool;
        this.tickRegistry = tickRegistry;
        init();
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
        sendMessage(WelcomeMessage.of());
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
                                Player newPlayer = Player.of(authenticatedUser, PromptSettings.defaultFormat());
                                playerRepository.savePlayer(newPlayer);
                                return newPlayer;
                            });
                        roomService.ensurePlayerLocation(player.getUsername());
                        registerEffects();
                        sendLook();
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

    private void handleCommand(String clientInput) throws IOException {
        String trimmed = clientInput.trim();
        if (trimmed.isEmpty()) {
            sendPrompt();
            return;
        }
        String[] parts = trimmed.split("\\s+", 2);
        String command = parts[0].toUpperCase(Locale.ROOT);
        String args = parts.length > 1 ? parts[1].trim() : "";

        if (isLookCommand(command)) {
            sendLook();
            return;
        }

        Optional<Direction> direction = parseDirection(command, args);
        if (direction.isPresent()) {
            sendMove(direction.get());
            return;
        }

        switch (command) {
            case "QUIT":
                CommandRegistry.QUIT.act().input(this);
                return;
            case "SAY":
                if (args.isEmpty()) {
                    writeLineWithPrompt("Say what?");
                } else {
                    CommandRegistry.SAY.act().message(player.getUsername(), args, clientPool.clients());
                    sendPrompt();
                }
                return;
            default:
                writeLineWithPrompt("Unknown command");
        }
    }

    private boolean isLookCommand(String command) {
        return command.equals("LOOK") || command.equals("L");
    }

    private Optional<Direction> parseDirection(String command, String args) {
        Optional<Direction> direct = Direction.fromInput(command);
        if (direct.isPresent()) {
            return direct;
        }
        if (command.equals("MOVE") || command.equals("GO") || command.equals("WALK")) {
            return Direction.fromInput(args);
        }
        return Optional.empty();
    }

    private void sendLook() {
        RoomService.LookResult result = roomService.look(player.getUsername());
        writeLinesWithPrompt(result.lines());
    }

    private void sendMove(Direction direction) {
        RoomService.MoveResult result = roomService.move(player.getUsername(), direction);
        writeLinesWithPrompt(result.lines());
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

    private void clearEffects() {
        for (TickSubscription subscription : effectSubscriptions) {
            subscription.unsubscribe();
        }
        effectSubscriptions.clear();
        effectsInitialized = false;
    }

    private void sendPrompt() {
        if (!authenticated || player == null) {
            return;
        }
        String format = player.getPromptFormat();
        if (format == null || format.isBlank() || format.equals(PromptSettings.LEGACY_FORMAT)) {
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
