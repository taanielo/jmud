package io.taanielo.jmud.core.server.socket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.SimpleMessage;
import io.taanielo.jmud.core.messaging.WelcomeMessage;
import io.taanielo.jmud.core.server.Client;

@Slf4j
public class SocketClient implements Client {
    private final Socket clientSocket;
    private final MessageBroadcaster messageBroadcaster;
    private OutputStream output;
    private BufferedReader input;
    private boolean connected;
    private String playerName;

    public SocketClient(Socket clientSocket, MessageBroadcaster messageBroadcaster) throws IOException {
        log.debug("Client connected");
        this.clientSocket = clientSocket;
        this.messageBroadcaster = messageBroadcaster;
    }

    @Override
    public void run() {
        log.debug("Initializing connection ..");
        try {
            output = clientSocket.getOutputStream();
            input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            connected = true;
        } catch (IOException e) {
            log.error("Error connecting client", e);
        }
        sendMessage(WelcomeMessage.of());
        boolean setPlayerName = true;


        String clientInput;
        try {
            while (connected && (clientInput = input.readLine()) != null) {
                log.debug("Received: {}", clientInput);
                if (setPlayerName) {
                    playerName = clientInput;
                    setPlayerName = false;
                }
                if ("quit".equals(clientInput)) {
                    close();
                } else if (clientInput.startsWith("say ")) {
                    Message say = SimpleMessage.of(clientInput.substring(4), playerName);
                    messageBroadcaster.broadcast(this, say);
                }
            }
        } catch (IOException e) {
            log.error("Error receiving", e);
        }
        log.info("Client disconnected");
    }

    @Override
    public void sendMessage(Message message) {
        try {
            message.send(output);
            output.flush();
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
