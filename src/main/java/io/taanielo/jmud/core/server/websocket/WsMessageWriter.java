package io.taanielo.jmud.core.server.websocket;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

import io.taanielo.jmud.core.messaging.MessageWriter;

/**
 * Writes game output as RFC 6455 text frames. Mirrors {@link
 * io.taanielo.jmud.core.server.ssh.SshMessageWriter}'s plain-writer approach — it never emits telnet
 * IAC negotiation bytes, and ANSI escape sequences pass through unmodified for the browser to render
 * (issue #526 §3). Each {@link #write(String)} maps to exactly one text frame; a shared lock keeps
 * concurrent writes (tick-thread broadcasts and prompts) from interleaving frame bytes.
 */
public class WsMessageWriter implements MessageWriter {

    private final OutputStream output;
    private final Object writeLock;

    public WsMessageWriter(OutputStream output, Object writeLock) {
        this.output = Objects.requireNonNull(output, "Output stream is required");
        this.writeLock = Objects.requireNonNull(writeLock, "Write lock is required");
    }

    @Override
    public void write(String message) throws IOException {
        byte[] frame = WebSocketFrame.encodeText(message);
        synchronized (writeLock) {
            WebSocketFrame.writeFrame(output, frame);
        }
    }
}
