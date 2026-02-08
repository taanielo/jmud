package io.taanielo.jmud.core.server.ssh;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.messaging.MessageWriter;
import io.taanielo.jmud.core.server.connection.ClientConnection;

/**
 * SSH-backed connection implementation.
 */
public class SshClientConnection implements ClientConnection {

    private final InputStream input;
    private final OutputStream output;
    private final MessageWriter messageWriter;
    private final BufferedReader reader;
    private final Object writeLock = new Object();

    public SshClientConnection(InputStream input, OutputStream output) {
        this.input = Objects.requireNonNull(input, "Input stream is required");
        this.output = Objects.requireNonNull(output, "Output stream is required");
        this.messageWriter = new SshMessageWriter(output);
        this.reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    @Override
    public void open() {
        // Streams are provided by the SSH subsystem and already open.
    }

    @Override
    public String readLine() throws IOException {
        return reader.readLine();
    }

    @Override
    public MessageWriter messageWriter() {
        return messageWriter;
    }

    @Override
    public void sendMessage(io.taanielo.jmud.core.messaging.Message message) throws IOException {
        synchronized (writeLock) {
            message.send(messageWriter);
        }
    }

    @Override
    public void writeLine(String message) {
        synchronized (writeLock) {
            try {
                messageWriter.write(message + "\r\n");
            } catch (IOException e) {
                // Ignore to match telnet write behavior.
            }
        }
    }

    @Override
    public void writeLines(List<String> lines) {
        for (String line : lines) {
            writeLine(line);
        }
    }

    @Override
    public void write(String text) {
        synchronized (writeLock) {
            try {
                messageWriter.write(text);
            } catch (IOException e) {
                // Ignore to match telnet write behavior.
            }
        }
    }

    @Override
    public void close() {
        try {
            input.close();
        } catch (IOException e) {
            // Ignore.
        }
        try {
            output.close();
        } catch (IOException e) {
            // Ignore.
        }
    }
}
