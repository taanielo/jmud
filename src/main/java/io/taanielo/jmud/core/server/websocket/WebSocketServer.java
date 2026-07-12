package io.taanielo.jmud.core.server.websocket;

import java.io.IOException;
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
 * WebSocket server endpoint (issue #526). Mirrors {@link
 * io.taanielo.jmud.core.server.socket.SocketServer}: a blocking accept loop hands each accepted
 * socket to a {@link WsClientConnection} and a regular {@link SocketClient}, which the client pool
 * runs on its own virtual thread. The transport carries the same in-band login flow and single-
 * writer tick model as telnet — only the wire framing (RFC 6455 text frames) differs.
 */
@Slf4j
public class WebSocketServer implements Server {

    private final String host;
    private final int port;
    private final GameContext context;
    private final ClientPool clientPool;
    private final WsOriginPolicy originPolicy;
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private volatile @Nullable ServerSocket serverSocket;

    public WebSocketServer(String host, int port, GameContext context, ClientPool clientPool, WsOriginPolicy originPolicy) {
        this.host = Objects.requireNonNull(host, "Host is required");
        this.port = port;
        this.context = Objects.requireNonNull(context, "Game context is required");
        this.clientPool = Objects.requireNonNull(clientPool, "Client pool is required");
        this.originPolicy = Objects.requireNonNull(originPolicy, "Origin policy is required");
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
                WsClientConnection connection = new WsClientConnection(clientSocket, originPolicy);
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

    private static String remoteAddress(Socket socket) {
        SocketAddress address = socket.getRemoteSocketAddress();
        return address == null ? "unknown" : address.toString();
    }
}
