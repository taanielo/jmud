package io.taanielo.jmud.core.server.websocket;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.bootstrap.GameContext;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.Server;
import io.taanielo.jmud.core.server.connection.TransportSecurity;
import io.taanielo.jmud.core.server.socket.SocketClient;

/**
 * WebSocket server endpoint (issue #526) that also serves the static browser client (issue #527)
 * from the same port. Mirrors {@link io.taanielo.jmud.core.server.socket.SocketServer}: a blocking
 * accept loop hands each accepted socket to a per-connection virtual thread, which reads the opening
 * HTTP request and routes it — a {@code /ws}-style WebSocket upgrade becomes a {@link
 * WsClientConnection} + {@link SocketClient} game session run by the client pool, while any other
 * {@code GET} is served as a static asset by {@link StaticHttpContent}. The game transport carries
 * the same in-band login flow and single-writer tick model as telnet — only the wire framing (RFC
 * 6455 text frames) differs.
 */
@Slf4j
public class WebSocketServer implements Server {

    private final String host;
    private final int port;
    private final GameContext context;
    private final ClientPool clientPool;
    private final WsOriginPolicy originPolicy;
    private final StaticHttpContent staticContent;
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private volatile @Nullable ServerSocket serverSocket;

    public WebSocketServer(
        String host,
        int port,
        GameContext context,
        ClientPool clientPool,
        WsOriginPolicy originPolicy,
        StaticHttpContent staticContent
    ) {
        this.host = Objects.requireNonNull(host, "Host is required");
        this.port = port;
        this.context = Objects.requireNonNull(context, "Game context is required");
        this.clientPool = Objects.requireNonNull(clientPool, "Client pool is required");
        this.originPolicy = Objects.requireNonNull(originPolicy, "Origin policy is required");
        this.staticContent = Objects.requireNonNull(staticContent, "Static content handler is required");
    }

    @Override
    public void run() {
        log.debug("Starting WebSocket server @ port {}", port);
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress(host, port));
            serverSocket = server;
            //noinspection InfiniteLoopStatement
            while (true) {
                Socket clientSocket = server.accept();
                // Read the request and route off the accept thread so a slow client cannot block
                // new connections (AGENTS.md §5); virtual threads make one-per-connection cheap.
                Thread.ofVirtual().name("ws-route").start(() -> route(clientSocket));
            }
        } catch (IOException e) {
            if (stopping.get()) {
                log.info("WebSocket server stopped accepting connections.");
            } else {
                log.error("WebSocket server error", e);
            }
        } finally {
            serverSocket = null;
        }
    }

    @Override
    public void stop() {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }
        ServerSocket server = this.serverSocket;
        if (server == null) {
            return;
        }
        try {
            server.close();
        } catch (IOException e) {
            log.warn("Error closing WebSocket server socket", e);
        }
    }

    /**
     * Reads the opening HTTP request on the connection's own virtual thread and dispatches it: a
     * WebSocket upgrade is promoted to a full game session via the client pool; any other request is
     * answered by the static-content handler and the socket closed.
     */
    private void route(Socket clientSocket) {
        try {
            InputStream in = new BufferedInputStream(clientSocket.getInputStream());
            OutputStream out = new BufferedOutputStream(clientSocket.getOutputStream());
            WebSocketHandshake.Request request = WebSocketHandshake.readRequest(in);
            if (request == null) {
                closeQuietly(clientSocket);
                return;
            }
            if (request.isWebSocketUpgrade()) {
                startGameSession(clientSocket, in, out, request);
            } else {
                staticContent.serve(request, out);
                closeQuietly(clientSocket);
            }
        } catch (IOException e) {
            log.debug("Failed to route WebSocket connection", e);
            closeQuietly(clientSocket);
        }
    }

    private void startGameSession(Socket clientSocket, InputStream in, OutputStream out, WebSocketHandshake.Request request) {
        WsClientConnection connection = new WsClientConnection(clientSocket, in, out, request, originPolicy);
        SocketClient client = new SocketClient(
            connection,
            new WsAuthenticationService(
                connection,
                context.userRegistry(),
                context.authenticationPolicy(),
                context.authenticationLimiter(),
                remoteAddress(clientSocket)
            ),
            context,
            clientPool,
            null,
            false,
            null,
            TransportSecurity.INSECURE
        );
        clientPool.add(client);
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            // Ignore: best-effort close of a rejected/served connection.
        }
    }

    private static String remoteAddress(Socket socket) {
        SocketAddress address = socket.getRemoteSocketAddress();
        return address == null ? "unknown" : address.toString();
    }
}
