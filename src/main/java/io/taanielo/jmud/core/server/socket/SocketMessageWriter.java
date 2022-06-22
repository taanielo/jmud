package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.messaging.MessageWriter;

/**
 * Socket messages end with newline and must be flushed
 */
@Slf4j
public class SocketMessageWriter implements MessageWriter {

    private final Socket clientSocket;

    public SocketMessageWriter(Socket clientSocket) {this.clientSocket = clientSocket;}

    @Override
    public void write(String message) throws IOException {
        if (clientSocket.isOutputShutdown()) {
            return;
        }
        OutputStream output = clientSocket.getOutputStream();
        output.write(message.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }
}
