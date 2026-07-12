package io.taanielo.jmud.core.server.websocket;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

/**
 * Serves the browser web client (issue #527) as static files over the same embedded HTTP server that
 * hosts the WebSocket game endpoint. A plain {@code GET /} returns the single-page client so that a
 * single {@code --ws-port} gives a complete, playable URL; {@code GET /ws} is handled elsewhere as a
 * WebSocket upgrade and never reaches this class.
 *
 * <p>Reading files from disk is blocking I/O, but it runs on the per-connection virtual thread of the
 * {@link WebSocketServer} accept loop — never on the tick thread (AGENTS.md §5), so a slow read cannot
 * stall the world. Request-target parsing and file resolution are kept pure ({@link #respond(String,
 * String)} returns a value object) so they are unit-testable without a socket (AGENTS.md §10).
 */
@Slf4j
public final class StaticHttpContent {

    private static final String INDEX_FILE = "index.html";

    private final Path root;

    /**
     * Creates a handler serving files from the given directory (typically {@code web/} at the working
     * directory root, mirroring how game data is loaded from {@code data/}). The path is normalised to
     * an absolute path so traversal checks compare like-for-like.
     *
     * @param root the directory whose contents are exposed
     */
    public StaticHttpContent(Path root) {
        this.root = Objects.requireNonNull(root, "Web root is required").toAbsolutePath().normalize();
    }

    /**
     * Serves the request by writing a complete HTTP/1.1 response (status line, headers and body) to
     * the stream and flushing it. The connection is expected to be closed by the caller afterwards
     * ({@code Connection: close}).
     *
     * @param request the parsed opening request whose request line carries the method and target
     * @param out     the connection output stream
     * @throws IOException if writing the response fails
     */
    public void serve(WebSocketHandshake.Request request, OutputStream out) throws IOException {
        String[] parts = request.requestLine().split(" ", -1);
        String method = parts.length > 0 ? parts[0] : "";
        String target = parts.length > 1 ? parts[1] : "/";
        Response response = respond(method, target);
        write(out, response);
    }

    /**
     * Resolves a request method and target to a response value object without any I/O side effects
     * beyond reading the resolved file. Returns the appropriate status: {@code 405} for a non-GET
     * method, {@code 400}/{@code 403} for a malformed or traversing target, {@code 404} for a missing
     * file, and {@code 200} with the file contents otherwise.
     *
     * @param method the HTTP method (only {@code GET} is served)
     * @param target the raw request target (path plus optional query string)
     * @return the response to write
     */
    public Response respond(String method, String target) {
        if (!"GET".equalsIgnoreCase(method)) {
            return Response.text(405, "Method Not Allowed", "Method not allowed");
        }
        String path = stripQuery(target);
        String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);
        if (decoded.isEmpty() || decoded.charAt(0) != '/') {
            return Response.text(400, "Bad Request", "Bad request");
        }
        String relative = decoded.equals("/") ? INDEX_FILE : decoded.substring(1);
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            return Response.text(403, "Forbidden", "Forbidden");
        }
        if (!Files.isRegularFile(resolved)) {
            return Response.text(404, "Not Found", "Not found");
        }
        try {
            byte[] body = Files.readAllBytes(resolved);
            return new Response(200, "OK", contentType(resolved), body);
        } catch (IOException e) {
            log.debug("Failed to read static asset {}", resolved, e);
            return Response.text(500, "Internal Server Error", "Internal server error");
        }
    }

    private static void write(OutputStream out, Response response) throws IOException {
        String headers = "HTTP/1.1 " + response.status() + ' ' + response.reason() + "\r\n"
            + "Content-Type: " + response.contentType() + "\r\n"
            + "Content-Length: " + response.body().length + "\r\n"
            + "Cache-Control: no-cache\r\n"
            + "Connection: close\r\n"
            + "\r\n";
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        out.write(response.body());
        out.flush();
    }

    private static String stripQuery(String target) {
        int query = target.indexOf('?');
        return query >= 0 ? target.substring(0, query) : target;
    }

    private static String contentType(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return switch (extension(name)) {
            case "html" -> "text/html; charset=utf-8";
            case "js" -> "text/javascript; charset=utf-8";
            case "css" -> "text/css; charset=utf-8";
            case "json" -> "application/json; charset=utf-8";
            case "svg" -> "image/svg+xml";
            case "png" -> "image/png";
            case "ico" -> "image/x-icon";
            case "txt" -> "text/plain; charset=utf-8";
            default -> "application/octet-stream";
        };
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }

    /**
     * An HTTP response ready to be written: status code, reason phrase, content type and body bytes.
     *
     * @param status      the HTTP status code
     * @param reason      the HTTP reason phrase
     * @param contentType the {@code Content-Type} header value
     * @param body        the response body bytes
     */
    @SuppressWarnings("ArrayRecordComponent")
    public record Response(int status, String reason, String contentType, byte[] body) {

        /** Defensive copy so the record stays an immutable value object despite its array component. */
        public Response {
            body = body.clone();
        }

        /** Returns a defensive copy of the body bytes. */
        @Override
        public byte[] body() {
            return body.clone();
        }

        private static Response text(int status, String reason, String message) {
            return new Response(status, reason, "text/plain; charset=utf-8", message.getBytes(StandardCharsets.UTF_8));
        }

        /** Returns the body decoded as UTF-8 text (convenience for tests). */
        public String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}
