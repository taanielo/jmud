package io.taanielo.jmud.core.server.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

class WsClientConnectionTest {

    private static final String HANDSHAKE = """
        GET / HTTP/1.1\r
        Host: localhost\r
        Upgrade: websocket\r
        Connection: Upgrade\r
        Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r
        Sec-WebSocket-Version: 13\r
        Origin: http://localhost\r
        \r
        """;

    @Test
    void completesHandshakeThenReadsAndWritesFrames() throws IOException {
        ByteArrayOutputStream inbound = new ByteArrayOutputStream();
        inbound.writeBytes(HANDSHAKE.getBytes(StandardCharsets.US_ASCII));
        inbound.writeBytes(maskedTextFrame("score\n"));
        FakeSocket socket = new FakeSocket(inbound.toByteArray());
        WsClientConnection connection = new WsClientConnection(socket, WsOriginPolicy.permissive());

        connection.open();
        String handshakeResponse = socket.captured().toString(StandardCharsets.US_ASCII);
        assertTrue(handshakeResponse.startsWith("HTTP/1.1 101 Switching Protocols\r\n"));
        assertTrue(handshakeResponse.contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo="));

        int afterHandshake = socket.captured().size();
        assertEquals("score", connection.readLine());

        connection.writeLine("You have 20 hp.");
        byte[] frameBytes = trailingBytes(socket.captured().toByteArray(), afterHandshake);
        WebSocketFrame frame = WebSocketFrame.readFrame(new ByteArrayInputStream(frameBytes));
        assertEquals("You have 20 hp.\r\n", frame.textPayload());
    }

    @Test
    void passesAnsiSequencesThroughUnmodified() throws IOException {
        ByteArrayOutputStream inbound = new ByteArrayOutputStream();
        inbound.writeBytes(HANDSHAKE.getBytes(StandardCharsets.US_ASCII));
        FakeSocket socket = new FakeSocket(inbound.toByteArray());
        WsClientConnection connection = new WsClientConnection(socket, WsOriginPolicy.permissive());
        connection.open();
        int afterHandshake = socket.captured().size();

        String ansi = "[31mHP[0m";
        connection.write(ansi);

        byte[] frameBytes = trailingBytes(socket.captured().toByteArray(), afterHandshake);
        WebSocketFrame frame = WebSocketFrame.readFrame(new ByteArrayInputStream(frameBytes));
        assertEquals(ansi, frame.textPayload());
    }

    @Test
    void emitsNoTelnetControlBytesOnTheWire() throws IOException {
        ByteArrayOutputStream inbound = new ByteArrayOutputStream();
        inbound.writeBytes(HANDSHAKE.getBytes(StandardCharsets.US_ASCII));
        FakeSocket socket = new FakeSocket(inbound.toByteArray());
        WsClientConnection connection = new WsClientConnection(socket, WsOriginPolicy.permissive());
        connection.open();
        int afterHandshake = socket.captured().size();

        connection.writeLine("Enter password: ");

        byte[] frameBytes = trailingBytes(socket.captured().toByteArray(), afterHandshake);
        for (byte b : frameBytes) {
            int unsigned = b & 0xFF;
            assertTrue(unsigned != 0xFF && unsigned != 0xFB && unsigned != 0xFC,
                "WebSocket wire must not carry telnet IAC bytes");
        }
    }

    @Test
    void rejectsDisallowedOrigin() {
        ByteArrayOutputStream inbound = new ByteArrayOutputStream();
        inbound.writeBytes(HANDSHAKE.getBytes(StandardCharsets.US_ASCII));
        FakeSocket socket = new FakeSocket(inbound.toByteArray());
        WsOriginPolicy policy = WsOriginPolicy.of(List.of("https://allowed.example"));
        WsClientConnection connection = new WsClientConnection(socket, policy);

        assertThrows(IOException.class, connection::open);
        assertTrue(socket.captured().toString(StandardCharsets.US_ASCII).startsWith("HTTP/1.1 403"));
    }

    @Test
    void rejectsNonUpgradeRequest() {
        FakeSocket socket = new FakeSocket("GET / HTTP/1.1\r\nHost: x\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        WsClientConnection connection = new WsClientConnection(socket, WsOriginPolicy.permissive());

        assertThrows(IOException.class, connection::open);
        assertTrue(socket.captured().toString(StandardCharsets.US_ASCII).startsWith("HTTP/1.1 400"));
    }

    private static byte[] trailingBytes(byte[] all, int offset) {
        byte[] result = new byte[all.length - offset];
        System.arraycopy(all, offset, result, 0, result.length);
        return result;
    }

    private static byte[] maskedTextFrame(String text) {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        byte[] mask = {0x11, 0x22, 0x33, 0x44};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x81);
        out.write(0x80 | payload.length);
        out.write(mask, 0, 4);
        for (int i = 0; i < payload.length; i++) {
            out.write(payload[i] ^ mask[i & 3]);
        }
        return out.toByteArray();
    }

    private static final class FakeSocket extends Socket {
        private final InputStream inputStream;
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        private FakeSocket(byte[] input) {
            this.inputStream = new ByteArrayInputStream(input);
        }

        private ByteArrayOutputStream captured() {
            return outputStream;
        }

        @Override
        public InputStream getInputStream() {
            return inputStream;
        }

        @Override
        public OutputStream getOutputStream() {
            return outputStream;
        }

        @Override
        public void close() {
            // No-op for the in-memory fake.
        }
    }
}
