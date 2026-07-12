package io.taanielo.jmud.core.server.websocket;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.messaging.MessageWriter;
import io.taanielo.jmud.core.server.connection.ClientConnection;

/**
 * WebSocket-backed {@link ClientConnection}. The RFC 6455 opening handshake runs in {@link #open()}
 * (on the per-connection virtual thread, AGENTS.md §5); thereafter each inbound text frame is one
 * line of player input (trailing newline stripped) and each outbound write is a text frame. No
 * telnet control bytes are emitted (issue #526 §3).
 */
@Slf4j
public class WsClientConnection implements ClientConnection {

    private final Socket socket;
    private final WsOriginPolicy originPolicy;
    private final Object writeLock = new Object();

    private @Nullable InputStream input;
    private @Nullable OutputStream output;
    private @Nullable MessageWriter messageWriter;

    public WsClientConnection(Socket socket, WsOriginPolicy originPolicy) {
        this.socket = Objects.requireNonNull(socket, "Socket is required");
        this.originPolicy = Objects.requireNonNull(originPolicy, "Origin policy is required");
    }

    @Override
    public void open() throws IOException {
        InputStream in = new BufferedInputStream(socket.getInputStream());
        OutputStream out = new BufferedOutputStream(socket.getOutputStream());
        WebSocketHandshake.Request request = WebSocketHandshake.readRequest(in);
        if (request == null || !request.isWebSocketUpgrade()) {
            reject(out, 400, "Bad Request", "Not a WebSocket upgrade request");
            throw new IOException("Invalid WebSocket handshake request");
        }
        if (!originPolicy.isAllowed(request.origin().orElse(null))) {
            reject(out, 403, "Forbidden", "Origin not allowed: " + request.origin().orElse("<none>"));
            throw new IOException("Rejected WebSocket origin");
        }
        String key = request.key().orElseThrow(() -> new IOException("Missing Sec-WebSocket-Key"));
        out.write(WebSocketHandshake.successResponse(key).getBytes(StandardCharsets.US_ASCII));
        out.flush();
        this.input = in;
        this.output = out;
        this.messageWriter = new WsMessageWriter(out, writeLock);
    }

    private void reject(OutputStream out, int status, String reason, String logMessage) {
        log.debug("Rejecting WebSocket handshake ({}): {}", status, logMessage);
        try {
            out.write(WebSocketHandshake.errorResponse(status, reason).getBytes(StandardCharsets.US_ASCII));
            out.flush();
        } catch (IOException e) {
            log.debug("Failed to write WebSocket rejection response", e);
        }
    }

    @Override
    public @Nullable String readLine() throws IOException {
        InputStream in = input;
        if (in == null) {
            throw new IllegalStateException("WebSocket connection is not open");
        }
        ByteArrayOutputStream messageBuffer = new ByteArrayOutputStream();
        while (true) {
            WebSocketFrame frame = WebSocketFrame.readFrame(in);
            if (frame == null) {
                return null;
            }
            switch (frame.opcode()) {
                case WebSocketFrame.OPCODE_CLOSE -> {
                    writeControl(WebSocketFrame.encodeClose());
                    return null;
                }
                case WebSocketFrame.OPCODE_PING -> writeControl(WebSocketFrame.encodePong(frame.payload()));
                case WebSocketFrame.OPCODE_PONG -> {
                    // Unsolicited pong; ignore per RFC 6455.
                }
                case WebSocketFrame.OPCODE_TEXT, WebSocketFrame.OPCODE_CONTINUATION -> {
                    messageBuffer.writeBytes(frame.payload());
                    if (frame.fin()) {
                        return stripTrailingNewline(messageBuffer.toString(StandardCharsets.UTF_8));
                    }
                }
                default -> {
                    // Binary or reserved opcode: not part of the game protocol, ignore its payload.
                }
            }
        }
    }

    private static String stripTrailingNewline(String text) {
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r')) {
            end--;
        }
        return text.substring(0, end);
    }

    private void writeControl(byte[] frame) {
        OutputStream out = output;
        if (out == null) {
            return;
        }
        synchronized (writeLock) {
            try {
                WebSocketFrame.writeFrame(out, frame);
            } catch (IOException e) {
                log.debug("Failed to write WebSocket control frame", e);
            }
        }
    }

    @Override
    public MessageWriter messageWriter() {
        return requireOpen();
    }

    @Override
    public void sendMessage(Message message) throws IOException {
        MessageWriter writer = requireOpen();
        // Hold the lock across the whole message so its lines cannot interleave with a concurrent
        // broadcast/prompt frame; the writer re-acquires the same (reentrant) lock per frame.
        synchronized (writeLock) {
            message.send(writer);
        }
    }

    @Override
    public void writeLine(String message) {
        write(message + "\r\n");
    }

    @Override
    public void writeLines(List<String> lines) {
        for (String line : lines) {
            writeLine(line);
        }
    }

    @Override
    public void write(String text) {
        MessageWriter writer = messageWriter;
        if (writer == null) {
            return;
        }
        try {
            writer.write(text);
        } catch (IOException e) {
            // Ignore to match telnet/SSH write behavior.
        }
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            // Ignore.
        }
    }

    private MessageWriter requireOpen() {
        MessageWriter writer = messageWriter;
        if (writer == null) {
            throw new IllegalStateException("WebSocket connection is not open");
        }
        return writer;
    }
}
