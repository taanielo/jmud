package io.taanielo.jmud.core.server.ssh;

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
    private final InputStreamReader reader;
    private final Object writeLock = new Object();

    public SshClientConnection(InputStream input, OutputStream output) {
        this(input, output, false);
    }

    public SshClientConnection(InputStream input, OutputStream output, boolean echoInput) {
        this.input = Objects.requireNonNull(input, "Input stream is required");
        this.output = Objects.requireNonNull(output, "Output stream is required");
        this.messageWriter = new SshMessageWriter(output);
        InputStream effectiveInput = echoInput ? new EchoingInputStream(input, output, writeLock) : input;
        this.reader = new InputStreamReader(effectiveInput, StandardCharsets.UTF_8);
    }

    @Override
    public void open() {
        // Streams are provided by the SSH subsystem and already open.
    }

    @Override
    public String readLine() throws IOException {
        StringBuilder buffer = new StringBuilder();
        boolean sawCarriageReturn = false;
        while (true) {
            int value = reader.read();
            if (value == -1) {
                return buffer.isEmpty() ? null : buffer.toString();
            }
            if (sawCarriageReturn) {
                sawCarriageReturn = false;
                if (value == '\n') {
                    return buffer.toString();
                }
            }
            if (value == '\r') {
                sawCarriageReturn = true;
                return buffer.toString();
            }
            if (value == '\n') {
                return buffer.toString();
            }
            if (value == 0x7F || value == 0x08) {
                if (!buffer.isEmpty()) {
                    buffer.deleteCharAt(buffer.length() - 1);
                }
                continue;
            }
            buffer.append((char) value);
        }
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

    private static final class EchoingInputStream extends InputStream {

        private final InputStream delegate;
        private final OutputStream output;
        private final Object writeLock;
        private int columnCount;
        private boolean skipNextLf;

        private EchoingInputStream(InputStream delegate, OutputStream output, Object writeLock) {
            this.delegate = delegate;
            this.output = output;
            this.writeLock = writeLock;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                echoByte((byte) value);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                for (int i = 0; i < count; i++) {
                    echoByte(buffer[offset + i]);
                }
            }
            return count;
        }

        private void echoByte(byte value) throws IOException {
            if (skipNextLf) {
                skipNextLf = false;
                if (value == '\n') {
                    return;
                }
            }
            if (value == 0x7F || value == 0x08) {
                handleBackspace();
                return;
            }
            if (value == '\r' || value == '\n') {
                columnCount = 0;
                if (value == '\r') {
                    skipNextLf = true;
                }
                writeNewline();
                return;
            } else {
                columnCount++;
            }
            synchronized (writeLock) {
                output.write(value);
                output.flush();
            }
        }

        private void handleBackspace() throws IOException {
            if (columnCount <= 0) {
                return;
            }
            columnCount--;
            synchronized (writeLock) {
                output.write('\b');
                output.write(' ');
                output.write('\b');
                output.flush();
            }
        }

        private void writeNewline() throws IOException {
            synchronized (writeLock) {
                output.write('\r');
                output.write('\n');
                output.flush();
            }
        }
    }
}
