package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.Server;
import io.taanielo.jmud.core.server.connection.TransportSecurity;

@Slf4j
public class SocketServer implements Server {
    private final int port;
    private final String host;

    private final ClientPool clientPool;
    private final GameContext context;

    public SocketServer(String host, int port, GameContext context, ClientPool clientPool) {
        this.host = Objects.requireNonNull(host, "Host is required");
        this.port = port;
        this.context = context;
        this.clientPool = clientPool;
    }

    @Override
    public void run() {
        log.debug("Starting server @ port {}", port);

        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress(host, port));
            //noinspection InfiniteLoopStatement
            while (true) {
                Socket clientSocket = server.accept();
                TelnetClientConnection connection = new TelnetClientConnection(clientSocket);
                SocketClient client = new SocketClient(
                    connection,
                    new SocketAuthenticationService(
                        clientSocket,
                        context.userRegistry(),
                        connection.messageWriter(),
                        context.authenticationPolicy(),
                        context.authenticationLimiter()
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
            log.error("Server error", e);
        }

    }
}
