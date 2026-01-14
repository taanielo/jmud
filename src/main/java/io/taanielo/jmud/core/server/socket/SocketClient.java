package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import io.taanielo.jmud.core.command.CommandHandler;
import io.taanielo.jmud.core.command.QuitCommand;
import io.taanielo.jmud.core.command.SayCommand;
import io.taanielo.jmud.core.server.ClientContext;
import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.authentication.AuthenticationService;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.UserRegistry;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.MessageWriter;
import io.taanielo.jmud.core.messaging.WelcomeMessage;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.server.Client;

@Slf4j
public class SocketClient implements Client {

    private final Socket clientSocket;
    private final MessageBroadcaster messageBroadcaster;
    private final MessageWriter messageWriter;
    private final AuthenticationService authenticationService;
    private final CommandHandler commandHandler;
    private final PlayerRepository playerRepository;

    private OutputStream output;
    private InputStream input;
    private boolean connected;

    private boolean authenticated;
    private Player player;

    public SocketClient(Socket clientSocket, MessageBroadcaster messageBroadcaster, UserRegistry userRegistry, PlayerRepository playerRepository) throws IOException {
        this.clientSocket = clientSocket;
        this.messageBroadcaster = messageBroadcaster;
        this.messageWriter = new SocketMessageWriter(clientSocket);
        this.authenticationService = new SocketAuthenticationService(clientSocket, userRegistry, messageWriter);
        this.commandHandler = new CommandHandler();
        this.playerRepository = playerRepository;
        init();
    }

    private void init() {
        commandHandler.register("quit", new QuitCommand());
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
                        // initialize post-authentication commands
                        var context = new ClientContext(player, messageBroadcaster);
                        commandHandler.register("say", new SayCommand(context));
                    });
                } else {
                    commandHandler.handle(this, clientInput);
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
            message.send(messageWriter);
        } catch (IOException e) {
            log.error("Error sending message", e);
        }
    }

    @Override
    public void close() {
        log.debug("Closing connection ..");
        connected = false;
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
}
