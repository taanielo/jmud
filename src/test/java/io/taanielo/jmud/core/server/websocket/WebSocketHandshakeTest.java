package io.taanielo.jmud.core.server.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebSocketHandshakeTest {

    @Test
    void computesRfc6455AcceptKey() {
        // The canonical example from RFC 6455 section 1.3.
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
            WebSocketHandshake.acceptKey("dGhlIHNhbXBsZSBub25jZQ=="));
    }

    @Test
    void parsesRequestLineAndHeadersCaseInsensitively() {
        WebSocketHandshake.Request request = WebSocketHandshake.parse("""
            GET /play HTTP/1.1\r
            Host: localhost:8080\r
            Upgrade: websocket\r
            Connection: Upgrade\r
            Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r
            Sec-WebSocket-Version: 13\r
            Origin: http://localhost:8080\r
            """);

        assertEquals("GET /play HTTP/1.1", request.requestLine());
        assertEquals("websocket", request.header("UPGRADE").orElseThrow());
        assertEquals("http://localhost:8080", request.origin().orElseThrow());
        assertEquals("dGhlIHNhbXBsZSBub25jZQ==", request.key().orElseThrow());
    }

    @Test
    void recognisesValidUpgradeRequest() {
        WebSocketHandshake.Request request = WebSocketHandshake.parse("""
            GET / HTTP/1.1\r
            Upgrade: websocket\r
            Connection: keep-alive, Upgrade\r
            Sec-WebSocket-Key: abc\r
            Sec-WebSocket-Version: 13\r
            """);

        assertTrue(request.isWebSocketUpgrade());
    }

    @Test
    void rejectsRequestMissingVersion() {
        WebSocketHandshake.Request request = WebSocketHandshake.parse("""
            GET / HTTP/1.1\r
            Upgrade: websocket\r
            Connection: Upgrade\r
            Sec-WebSocket-Key: abc\r
            """);

        assertFalse(request.isWebSocketUpgrade());
    }

    @Test
    void rejectsNonGetRequest() {
        WebSocketHandshake.Request request = WebSocketHandshake.parse("""
            POST / HTTP/1.1\r
            Upgrade: websocket\r
            Connection: Upgrade\r
            Sec-WebSocket-Key: abc\r
            Sec-WebSocket-Version: 13\r
            """);

        assertFalse(request.isWebSocketUpgrade());
    }

    @Test
    void successResponseCarriesAcceptHeaderAndStatusLine() {
        String response = WebSocketHandshake.successResponse("dGhlIHNhbXBsZSBub25jZQ==");

        assertTrue(response.startsWith("HTTP/1.1 101 Switching Protocols\r\n"));
        assertTrue(response.contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n"));
        assertTrue(response.endsWith("\r\n\r\n"));
    }
}
