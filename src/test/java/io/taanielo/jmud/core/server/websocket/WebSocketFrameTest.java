package io.taanielo.jmud.core.server.websocket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class WebSocketFrameTest {

    @Test
    void encodesAndDecodesTextRoundTrip() throws IOException {
        byte[] encoded = WebSocketFrame.encodeText("hello world");

        WebSocketFrame frame = WebSocketFrame.readFrame(new ByteArrayInputStream(encoded));

        assertEquals(WebSocketFrame.OPCODE_TEXT, frame.opcode());
        assertTrue(frame.fin());
        assertEquals("hello world", frame.textPayload());
    }

    @Test
    void serverFramesAreNotMasked() {
        byte[] encoded = WebSocketFrame.encodeText("x");

        // Second byte holds the mask bit (0x80) and length; server frames must never set it.
        assertEquals(0, encoded[1] & 0x80);
    }

    @Test
    void preservesAnsiEscapeSequencesUnmodified() throws IOException {
        String ansi = "[31mred[0m";
        byte[] encoded = WebSocketFrame.encodeText(ansi);

        WebSocketFrame frame = WebSocketFrame.readFrame(new ByteArrayInputStream(encoded));

        assertEquals(ansi, frame.textPayload());
    }

    @Test
    void decodesMaskedClientFrame() throws IOException {
        byte[] payload = "login".getBytes(StandardCharsets.UTF_8);
        byte[] mask = {0x12, 0x34, 0x56, 0x78};
        byte[] frameBytes = maskedTextFrame(payload, mask);

        WebSocketFrame frame = WebSocketFrame.readFrame(new ByteArrayInputStream(frameBytes));

        assertEquals("login", frame.textPayload());
    }

    @Test
    void handlesTwoByteLengthBoundary() throws IOException {
        String payload = "a".repeat(126);
        byte[] encoded = WebSocketFrame.encodeText(payload);

        assertEquals(126, encoded[1] & 0x7F);
        assertEquals(payload, WebSocketFrame.readFrame(new ByteArrayInputStream(encoded)).textPayload());
    }

    @Test
    void handlesEightByteLengthBoundary() throws IOException {
        String payload = "b".repeat(70000);
        byte[] encoded = WebSocketFrame.encodeText(payload);

        assertEquals(127, encoded[1] & 0x7F);
        assertEquals(payload, WebSocketFrame.readFrame(new ByteArrayInputStream(encoded)).textPayload());
    }

    @Test
    void encodesCloseAndPongControlFrames() throws IOException {
        WebSocketFrame close = WebSocketFrame.readFrame(new ByteArrayInputStream(WebSocketFrame.encodeClose()));
        assertEquals(WebSocketFrame.OPCODE_CLOSE, close.opcode());

        byte[] appData = {1, 2, 3};
        WebSocketFrame pong = WebSocketFrame.readFrame(new ByteArrayInputStream(WebSocketFrame.encodePong(appData)));
        assertEquals(WebSocketFrame.OPCODE_PONG, pong.opcode());
        assertArrayEquals(appData, pong.payload());
    }

    @Test
    void returnsNullOnEndOfStream() throws IOException {
        assertNull(WebSocketFrame.readFrame(new ByteArrayInputStream(new byte[0])));
    }

    @Test
    void rejectsOversizedPayloadLength() {
        // 8-byte length header advertising a payload far beyond the safety cap.
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        header.write(0x81);
        header.write(0xFF); // masked + 127 length indicator.
        for (int i = 0; i < 8; i++) {
            header.write(i == 0 ? 0x7F : 0xFF);
        }
        IOException thrown = assertThrows(IOException.class,
            () -> WebSocketFrame.readFrame(new ByteArrayInputStream(header.toByteArray())));
        assertTrue(thrown.getMessage().contains("too large"));
    }

    private static byte[] maskedTextFrame(byte[] payload, byte[] mask) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x81); // FIN + text opcode.
        out.write(0x80 | payload.length); // mask bit + 7-bit length (payload < 126 in tests).
        out.write(mask, 0, 4);
        for (int i = 0; i < payload.length; i++) {
            out.write(payload[i] ^ mask[i & 3]);
        }
        return out.toByteArray();
    }
}
