package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageWriter;

/**
 * Raw telnet I/O layer wrapping a socket connection.
 *
 * <p>Handles byte-level reading, IAC detection, and thread-safe writing.
 * All write methods synchronize on an internal lock so callers from
 * different threads (command thread, tick thread) do not interleave output.
 */
@Slf4j
public class TelnetConnection {

    private final Socket socket;
    private final MessageWriter messageWriter;
    private final Object writeLock = new Object();
    private InputStream input;
    private OutputStream output;

    /**
     * Creates a telnet connection wrapping the given socket.
     */
    public TelnetConnection(Socket socket) {
        this.socket = Objects.requireNonNull(socket, "Socket is required");
        this.messageWriter = new SocketMessageWriter(socket);
    }

    /**
     * Opens the input and output streams for this connection.
     *
     * @throws IOException if the streams cannot be opened
     */
    public void open() throws IOException {
        this.output = socket.getOutputStream();
        this.input = socket.getInputStream();
    }

    /**
     * Returns the underlying input stream for byte-level reading.
     */
    public InputStream input() {
        return input;
    }

    /**
     * Returns the underlying message writer.
     */
    public MessageWriter messageWriter() {
        return messageWriter;
    }

    /**
     * Sends a structured message through the writer under the write lock.
     */
    public void sendMessage(Message message) throws IOException {
        synchronized (writeLock) {
            message.send(messageWriter);
        }
    }

    /**
     * Writes a line of text followed by CR+LF, synchronized.
     */
    public void writeLine(String message) {
        synchronized (writeLock) {
            try {
                messageWriter.writeLine(message);
            } catch (IOException e) {
                log.error("Error writing message", e);
            }
        }
    }

    /**
     * Writes multiple lines, each followed by CR+LF, synchronized.
     */
    public void writeLines(List<String> lines) {
        for (String line : lines) {
            writeLine(line);
        }
    }

    /**
     * Writes raw text without a trailing newline, synchronized.
     */
    public void write(String text) {
        synchronized (writeLock) {
            try {
                messageWriter.write(text);
            } catch (IOException e) {
                log.error("Error writing", e);
            }
        }
    }

    /**
     * Closes the streams and socket.
     */
    public void close() {
        try {
            if (input != null) {
                input.close();
            }
        } catch (IOException e) {
            log.error("Cannot close input stream", e);
        }
        try {
            if (output != null) {
                output.close();
            }
        } catch (IOException e) {
            log.error("Cannot close output stream", e);
        }
        try {
            if (socket.isConnected()) {
                socket.close();
            }
        } catch (IOException e) {
            log.error("Error closing socket", e);
        }
    }
}
