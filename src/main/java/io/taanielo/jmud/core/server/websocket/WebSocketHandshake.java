package io.taanielo.jmud.core.server.websocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * Parses and validates the RFC 6455 opening handshake and computes the {@code Sec-WebSocket-Accept}
 * response value. Kept pure (string/stream in, values out) so the accept-key computation and header
 * parsing are unit-testable without a socket (AGENTS.md §10).
 */
public final class WebSocketHandshake {

    /** RFC 6455 GUID appended to the client key before hashing. */
    private static final String MAGIC_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private static final int MAX_HEADER_BYTES = 16 * 1024;

    private WebSocketHandshake() {
    }

    /**
     * Reads the HTTP request line and headers up to the terminating blank line.
     *
     * @return the parsed request, or {@code null} on end of stream before any header was read
     * @throws IOException if the request is malformed or exceeds the header size limit
     */
    public static @Nullable Request readRequest(InputStream in) throws IOException {
        String raw = readHeaderBlock(in);
        if (raw == null) {
            return null;
        }
        return parse(raw);
    }

    /**
     * Parses an already-read request/header block (request line plus {@code CRLF}-separated
     * headers). Header names are lower-cased for case-insensitive lookup.
     */
    public static Request parse(String raw) {
        String[] lines = raw.split("\r\n", -1);
        String requestLine = lines.length > 0 ? lines[0] : "";
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(colon + 1).trim();
                headers.put(name, value);
            }
        }
        return new Request(requestLine, Map.copyOf(headers));
    }

    /**
     * Computes the {@code Sec-WebSocket-Accept} header value for a client key: the Base64 of the
     * SHA-1 of the key concatenated with the RFC 6455 GUID.
     */
    public static String acceptKey(String secWebSocketKey) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((secWebSocketKey + MAGIC_GUID).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required for the WebSocket handshake", e);
        }
    }

    /** Builds the HTTP 101 switching-protocols response for a validated request. */
    public static String successResponse(String secWebSocketKey) {
        return "HTTP/1.1 101 Switching Protocols\r\n"
            + "Upgrade: websocket\r\n"
            + "Connection: Upgrade\r\n"
            + "Sec-WebSocket-Accept: " + acceptKey(secWebSocketKey) + "\r\n"
            + "\r\n";
    }

    /** Builds a short HTTP error response used when the handshake is rejected. */
    public static String errorResponse(int status, String reason) {
        return "HTTP/1.1 " + status + " " + reason + "\r\n"
            + "Connection: close\r\n"
            + "Content-Length: 0\r\n"
            + "\r\n";
    }

    private static @Nullable String readHeaderBlock(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int state = 0; // Counts progress through the terminating \r\n\r\n sequence.
        int value;
        while ((value = in.read()) != -1) {
            buffer.write(value);
            if (buffer.size() > MAX_HEADER_BYTES) {
                throw new IOException("WebSocket handshake headers exceed size limit");
            }
            state = switch (value) {
                case '\r' -> (state == 2) ? 3 : 1;
                case '\n' -> (state == 1) ? 2 : (state == 3 ? 4 : 0);
                default -> 0;
            };
            if (state == 4) {
                return buffer.toString(StandardCharsets.UTF_8);
            }
        }
        return buffer.size() == 0 ? null : buffer.toString(StandardCharsets.UTF_8);
    }

    /**
     * A parsed opening-handshake request.
     *
     * @param requestLine the raw HTTP request line (e.g. {@code GET / HTTP/1.1})
     * @param headers     the request headers keyed by lower-cased name
     */
    public record Request(String requestLine, Map<String, String> headers) {

        /** Returns the value of a header (name is matched case-insensitively). */
        public Optional<String> header(String name) {
            return Optional.ofNullable(headers.get(name.toLowerCase(Locale.ROOT)));
        }

        /** Returns the {@code Origin} header if present. */
        public Optional<String> origin() {
            return header("origin");
        }

        /**
         * Returns whether this is a well-formed RFC 6455 upgrade request: a GET carrying an upgrade
         * to {@code websocket}, version 13, and a {@code Sec-WebSocket-Key}.
         */
        public boolean isWebSocketUpgrade() {
            boolean get = requestLine.regionMatches(true, 0, "GET ", 0, 4);
            boolean upgrade = header("upgrade").map(v -> v.equalsIgnoreCase("websocket")).orElse(false);
            boolean connection = header("connection")
                .map(v -> v.toLowerCase(Locale.ROOT).contains("upgrade")).orElse(false);
            boolean version = header("sec-websocket-version").map("13"::equals).orElse(false);
            return get && upgrade && connection && version && key().isPresent();
        }

        /** Returns the {@code Sec-WebSocket-Key} header if present. */
        public Optional<String> key() {
            return header("sec-websocket-key");
        }
    }
}
