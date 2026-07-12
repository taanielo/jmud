package io.taanielo.jmud.core.server.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaticHttpContentTest {

    @Test
    void servesIndexForRootPath(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("index.html"), "<!DOCTYPE html><title>jmud</title>");
        StaticHttpContent content = new StaticHttpContent(dir);

        StaticHttpContent.Response response = content.respond("GET", "/");

        assertEquals(200, response.status());
        assertEquals("text/html; charset=utf-8", response.contentType());
        assertTrue(response.bodyText().contains("jmud"));
    }

    @Test
    void servesNamedAssetWithMappedContentType(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("app.js"), "console.log('hi');");
        StaticHttpContent content = new StaticHttpContent(dir);

        StaticHttpContent.Response response = content.respond("GET", "/app.js");

        assertEquals(200, response.status());
        assertEquals("text/javascript; charset=utf-8", response.contentType());
    }

    @Test
    void ignoresQueryString(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("style.css"), "body{}");
        StaticHttpContent content = new StaticHttpContent(dir);

        StaticHttpContent.Response response = content.respond("GET", "/style.css?v=2");

        assertEquals(200, response.status());
        assertEquals("text/css; charset=utf-8", response.contentType());
    }

    @Test
    void returnsNotFoundForMissingFile(@TempDir Path dir) {
        StaticHttpContent content = new StaticHttpContent(dir);

        assertEquals(404, content.respond("GET", "/missing.js").status());
    }

    @Test
    void rejectsPathTraversal(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("index.html"), "root");
        Path secret = dir.getParent().resolve("secret.txt");
        Files.writeString(secret, "top secret");
        StaticHttpContent content = new StaticHttpContent(dir);

        StaticHttpContent.Response response = content.respond("GET", "/../secret.txt");

        assertTrue(response.status() == 403 || response.status() == 404,
            "traversal must not leak files outside the web root");
        Files.deleteIfExists(secret);
    }

    @Test
    void rejectsNonGetMethods(@TempDir Path dir) {
        StaticHttpContent content = new StaticHttpContent(dir);

        assertEquals(405, content.respond("POST", "/").status());
    }

    @Test
    void writesCompleteHttpResponse(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("index.html"), "hello");
        StaticHttpContent content = new StaticHttpContent(dir);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        WebSocketHandshake.Request request =
            new WebSocketHandshake.Request("GET / HTTP/1.1", Map.of());
        content.serve(request, out);

        String raw = out.toString(StandardCharsets.UTF_8);
        assertTrue(raw.startsWith("HTTP/1.1 200 OK\r\n"), raw);
        assertTrue(raw.contains("Content-Type: text/html; charset=utf-8\r\n"));
        assertTrue(raw.contains("Content-Length: 5\r\n"));
        assertTrue(raw.contains("Connection: close\r\n"));
        assertTrue(raw.endsWith("\r\n\r\nhello"));
    }
}
