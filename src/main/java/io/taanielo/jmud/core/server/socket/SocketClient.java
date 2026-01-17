package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
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
import io.taanielo.jmud.core.needs.NeedsSession;
import io.taanielo.jmud.core.needs.NeedsSettings;
import io.taanielo.jmud.core.needs.NeedsTickOutcome;
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
    private final Object writeLock = new Object();

    private OutputStream output;
    private InputStream input;
    private boolean connected;

    private boolean authenticated;
    private Player player;
    private volatile NeedsSession needsSession;

    public SocketClient(
        Socket clientSocket,
        MessageBroadcaster messageBroadcaster,
        UserRegistry userRegistry,
        PlayerRepository playerRepository,
        RoomService roomService,
        ClientPool clientPool
    ) throws IOException {
        this.clientSocket = clientSocket;
        this.messageBroadcaster = messageBroadcaster;
        this.messageWriter = new SocketMessageWriter(clientSocket);
        this.authenticationService = new SocketAuthenticationService(clientSocket, userRegistry, messageWriter);
        this.playerRepository = playerRepository;
        this.roomService = roomService;
        this.clientPool = clientPool;
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
                                Player newPlayer = Player.of(authenticatedUser);
                                playerRepository.savePlayer(newPlayer);
                                return newPlayer;
                            });
                        roomService.ensurePlayerLocation(player.getUsername());
                        ensureNeedsSession();
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
    }

    @Override
    public void close() {
        log.debug("Closing connection ..");
        connected = false;
        clearNeedsSession();
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
                    writeLineSafe("Say what?");
                } else {
                    CommandRegistry.SAY.act().message(player.getUsername(), args, clientPool.clients());
                }
                return;
            default:
                writeLineSafe("Unknown command");
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
        for (String line : result.lines()) {
            writeLineSafe(line);
        }
    }

    private void sendMove(Direction direction) {
        RoomService.MoveResult result = roomService.move(player.getUsername(), direction);
        for (String line : result.lines()) {
            writeLineSafe(line);
        }
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

    private void ensureNeedsSession() {
        if (!NeedsSettings.enabled()) {
            return;
        }
        if (needsSession == null) {
            needsSession = NeedsSession.forPlayer(player.getUsername());
        }
    }

    void tickNeeds() {
        if (!NeedsSettings.enabled()) {
            return;
        }
        NeedsSession current = needsSession;
        if (current == null) {
            return;
        }
        NeedsTickOutcome outcome = current.tick();
        needsSession = outcome.session();
        for (String message : outcome.messages()) {
            writeLineSafe(message);
        }
    }

    private void clearNeedsSession() {
        needsSession = null;
    }
}
