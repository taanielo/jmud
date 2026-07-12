package io.taanielo.jmud.core.server.websocket;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.Nullable;

/**
 * Minimal RFC 6455 frame reader/writer covering exactly what the game transport needs: inbound text
 * (possibly fragmented), ping, pong and close; outbound text, pong and close.
 *
 * <p>All methods are pure with respect to game state and operate only on the supplied streams, so
 * the {@link #encodeText(String)} / {@link #readFrame(java.io.InputStream)} pair is unit-testable
 * without any networking (AGENTS.md §10). Client-to-server frames are required by the RFC to be
 * masked; server-to-client frames are never masked.
 *
 * <p>The {@code byte[]} payload component is intentional: frame payloads are a hot-path, raw byte
 * buffer where an array is the natural, allocation-cheap representation. Immutability is preserved by
 * defensively cloning on the way in (compact constructor) and out ({@link #payload()} accessor), so
 * the {@code ArrayRecordComponent} warning is suppressed rather than the array replaced.
 *
 * @param opcode  the frame opcode (one of the {@code OPCODE_*} constants)
 * @param fin     whether the FIN bit was set (last fragment of a message)
 * @param payload the unmasked application payload
 */
@SuppressWarnings("ArrayRecordComponent")
public record WebSocketFrame(int opcode, boolean fin, byte[] payload) {

    /** Continuation frame. */
    public static final int OPCODE_CONTINUATION = 0x0;
    /** UTF-8 text frame. */
    public static final int OPCODE_TEXT = 0x1;
    /** Binary frame (unused by the game protocol, read then ignored). */
    public static final int OPCODE_BINARY = 0x2;
    /** Connection-close control frame. */
    public static final int OPCODE_CLOSE = 0x8;
    /** Ping control frame. */
    public static final int OPCODE_PING = 0x9;
    /** Pong control frame. */
    public static final int OPCODE_PONG = 0xA;

    private static final int MAX_PAYLOAD_BYTES = 1 << 20; // 1 MiB safety cap per frame.

    /** Defensive copy so the record stays an immutable value object despite its array component. */
    public WebSocketFrame {
        payload = payload.clone();
    }

    /** Returns a defensive copy of the payload bytes. */
    @Override
    public byte[] payload() {
        return payload.clone();
    }

    /**
     * Reads a single frame from the stream, unmasking the payload if a client mask is present.
     *
     * @return the decoded frame, or {@code null} on clean end of stream
     * @throws IOException if the stream fails or the frame violates the supported RFC 6455 subset
     */
    public static @Nullable WebSocketFrame readFrame(InputStream in) throws IOException {
        int b0 = in.read();
        if (b0 == -1) {
            return null;
        }
        int b1 = readByte(in);
        boolean fin = (b0 & 0x80) != 0;
        int opcode = b0 & 0x0F;
        boolean masked = (b1 & 0x80) != 0;
        long length = b1 & 0x7F;
        if (length == 126) {
            length = ((long) readByte(in) << 8) | readByte(in);
        } else if (length == 127) {
            length = 0;
            for (int i = 0; i < 8; i++) {
                length = (length << 8) | readByte(in);
            }
        }
        if (length < 0 || length > MAX_PAYLOAD_BYTES) {
            throw new IOException("WebSocket frame payload too large: " + length);
        }
        byte[] mask = new byte[4];
        if (masked) {
            readFully(in, mask, 4);
        }
        byte[] payload = new byte[(int) length];
        readFully(in, payload, payload.length);
        if (masked) {
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) (payload[i] ^ mask[i & 3]);
            }
        }
        return new WebSocketFrame(opcode, fin, payload);
    }

    /** Returns the payload decoded as UTF-8 text. */
    public String textPayload() {
        return new String(payload, StandardCharsets.UTF_8);
    }

    /** Encodes an unmasked server-to-client text frame. */
    public static byte[] encodeText(String text) {
        return encode(OPCODE_TEXT, text.getBytes(StandardCharsets.UTF_8));
    }

    /** Encodes an unmasked server-to-client pong frame echoing the supplied application data. */
    public static byte[] encodePong(byte[] applicationData) {
        return encode(OPCODE_PONG, applicationData);
    }

    /** Encodes an empty server-to-client close frame. */
    public static byte[] encodeClose() {
        return encode(OPCODE_CLOSE, new byte[0]);
    }

    private static byte[] encode(int opcode, byte[] payload) {
        int headerSize;
        if (payload.length <= 125) {
            headerSize = 2;
        } else if (payload.length <= 0xFFFF) {
            headerSize = 4;
        } else {
            headerSize = 10;
        }
        byte[] frame = new byte[headerSize + payload.length];
        frame[0] = (byte) (0x80 | (opcode & 0x0F)); // FIN + opcode.
        if (payload.length <= 125) {
            frame[1] = (byte) payload.length;
        } else if (payload.length <= 0xFFFF) {
            frame[1] = 126;
            frame[2] = (byte) ((payload.length >> 8) & 0xFF);
            frame[3] = (byte) (payload.length & 0xFF);
        } else {
            frame[1] = 127;
            long len = payload.length;
            for (int i = 0; i < 8; i++) {
                frame[2 + i] = (byte) ((len >> (8 * (7 - i))) & 0xFF);
            }
        }
        System.arraycopy(payload, 0, frame, headerSize, payload.length);
        return frame;
    }

    /** Writes a raw pre-encoded frame and flushes. */
    public static void writeFrame(OutputStream out, byte[] frame) throws IOException {
        out.write(frame);
        out.flush();
    }

    private static int readByte(InputStream in) throws IOException {
        int value = in.read();
        if (value == -1) {
            throw new EOFException("Unexpected end of WebSocket stream");
        }
        return value;
    }

    private static void readFully(InputStream in, byte[] buffer, int length) throws IOException {
        int offset = 0;
        while (offset < length) {
            int read = in.read(buffer, offset, length - offset);
            if (read == -1) {
                throw new EOFException("Unexpected end of WebSocket stream");
            }
            offset += read;
        }
    }
}
