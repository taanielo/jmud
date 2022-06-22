package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.UserRegistry;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.MessageWriter;
import io.taanielo.jmud.core.messaging.UserSayMessage;
import io.taanielo.jmud.core.messaging.WelcomeMessage;
import io.taanielo.jmud.core.server.Client;

@Slf4j
public class SocketClient implements Client {

    /**
     * Interpret as Command
     */
    private static final int IAC = -1;
    /**
     * Interrupt process (user pressed Ctrl + C)
     */
    private static final int IP = -12;

    private final Socket clientSocket;
    private final MessageBroadcaster messageBroadcaster;
    private final UserRegistry userRegistry;
    private final MessageWriter messageWriter;

    private OutputStream output;
    private InputStream input;
    private boolean connected;

    private boolean authenticated;
    private User user;

    public SocketClient(Socket clientSocket, MessageBroadcaster messageBroadcaster, UserRegistry userRegistry) throws IOException {
        this.userRegistry = userRegistry;
        log.debug("Client connected");
        this.clientSocket = clientSocket;
        this.messageBroadcaster = messageBroadcaster;
        this.messageWriter = new SocketMessageWriter(clientSocket);
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
        Username username = null;
        User authenticationUser = null;
        Password password;
        try {

            byte[] bytes = new byte[1024];
            int read;
            while (connected && (read = input.read(bytes)) != -1) {
                log.debug("Read: {}", read);
                if (bytes[0] == IAC) {
                    if (bytes[1] == IP) {
                        log.debug("Received IP, closing connection ..");
                        break;
                    } else {
                        // ignore IAC responses for now
                        log.debug("Received IAC [{}], skipping ..", bytes);
                    }
                    continue;
                }
                clientInput = readBytesIntoString(bytes);
                // quit should be always first if users doesn't want to authenticate
                log.debug("Received: \"{}\" [{}]", clientInput, bytes);
                if ("quit".equals(clientInput)) {
                    close();
                    break;
                }
                if (!authenticated) {
                    if (username == null) {
                        log.debug("Start authentication ..");
                        log.debug("Username received");
                        username = Username.of(clientInput);
                        Optional<User> existingUser = userRegistry.findByUsername(username);
                        if (existingUser.isPresent()) {
                            authenticationUser = existingUser.get();
                            log.debug("User exists: {}", authenticationUser.getUsername().getValue());
                            disableLocalEcho();
                            messageWriter.write("Enter password: ");
                        } else {
                            log.debug("User not found");
                            username = null;
                            // TODO taanielo 2022-06-22 create user
                            messageWriter.writeLine("User not found!");
                            messageWriter.write("Enter username: ");
                        }
                    } else {
                        log.debug("Password received");
                        password = Password.of(clientInput);
                        if (authenticationUser.getPassword().equals(password)) {
                            log.debug("Login successful");
                            messageWriter.writeLine();
                            messageWriter.writeLine("Login successful!");
                            enableLocalEcho();
                            authenticated = true;
                            user = authenticationUser;
                        } else {
                            log.debug("Password doesn't match, login unsuccessful");
                            username = null;
                            messageWriter.writeLine();
                            messageWriter.writeLine("Incorrect password!");
                            messageWriter.write("Enter username: ");
                            enableLocalEcho();
                        }
                    }
                } else {
                    //log.debug("Received: {}", clientInput);
                    if (clientInput.startsWith("say ")) {
                        Message say = UserSayMessage.of(clientInput.substring(4), user.getUsername());
                        messageBroadcaster.broadcast(this, say);
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

    private void disableLocalEcho() throws IOException {
        log.debug("Disabling local echo");
        output.write(IAC);
        output.write(0xFB);
        output.write(0x01);
        output.flush();
    }

    private void enableLocalEcho() throws IOException {
        log.debug("Enabling local echo");
        output.write(IAC);
        output.write(0xFC);
        output.write(0x01);
        output.flush();
    }

    private static String readBytesIntoString(byte[] bytes) {
        int crlfPos = 0;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == 13 && bytes[i + 1] == 10 || bytes[i] == 10) {
                crlfPos = i;
                break;
            }
        }
        return new String(bytes, 0, crlfPos, StandardCharsets.UTF_8);
    }
}
