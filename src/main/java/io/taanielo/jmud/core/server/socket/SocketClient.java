package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;

import io.taanielo.jmud.command.CommandRegistry;
import io.taanielo.jmud.core.authentication.AuthenticationService;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.UserRegistry;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.MessageWriter;
import io.taanielo.jmud.core.messaging.UserSayMessage;
import io.taanielo.jmud.core.messaging.WelcomeMessage;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientPool;

@Slf4j
public class SocketClient implements Client {

    private final Socket clientSocket;
    private final ClientPool clientPool;
    private final MessageWriter messageWriter;
    private final UserRegistry userRegistry;
    private final AuthenticationService authenticationService;

    private OutputStream output;
    private InputStream input;
    private boolean connected;

    private boolean authenticated;
    private User user;

    public SocketClient(Socket clientSocket, ClientPool clientPool, UserRegistry userRegistry) throws IOException {
        this.clientSocket = clientSocket;
        this.messageWriter = new SocketMessageWriter(clientSocket);
        this.clientPool = clientPool;
        this.userRegistry = userRegistry;
        authenticationService = new SocketAuthenticationService(clientSocket, userRegistry, messageWriter);
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
                // quit should be always first if users doesn't want to authenticate
                log.debug("Received: \"{}\" [{}]", clientInput, bytes);
                if (!authenticated) {
                    authenticationService.authenticate(clientInput, authenticatedUser -> {
                        authenticated = true;
                        user = authenticatedUser;
                    });
                } else {
                    String commandInput = clientInput.substring(0, clientInput.indexOf(" ")).toUpperCase(Locale.getDefault());
                    String commandInputArgs = clientInput.substring(clientInput.indexOf(" ") + 1);
                    switch (commandInput) {
                        case "QUIT" -> CommandRegistry.QUIT.act()
                                .input(this);
                        case "SAY" -> CommandRegistry.SAY.act()
                                .message(user.getUsername(), commandInputArgs, clientPool.clients());
                        default -> messageWriter.writeLine("Unknown command");
                    }
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

    public void sendMessage(UserSayMessage message) {
        if (message.getUsername().equals(user.getUsername())) {
            return;
        }
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
