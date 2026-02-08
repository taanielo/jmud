package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.Server;

@Slf4j
public class SocketServer implements Server {
    private final int port;

    private final ClientPool clientPool;
    private final GameContext context;

    public SocketServer(int port, GameContext context, ClientPool clientPool) {
        this.port = port;
        this.context = context;
        this.clientPool = clientPool;
    }

    @Override
    public void run() {
        log.debug("Starting server @ port {}", port);

        context.tickScheduler().start();
        try (ServerSocket server = new ServerSocket(port)) {
            //noinspection InfiniteLoopStatement
            while (true) {
                Socket clientSocket = server.accept();
                TelnetClientConnection connection = new TelnetClientConnection(clientSocket);
                SocketClient client = new SocketClient(
                    connection,
                    new SocketAuthenticationService(clientSocket, context.userRegistry(), connection.messageWriter()),
                    context,
                    clientPool,
                    null,
                    false,
                    null
                );
                clientPool.add(client);
            }
        } catch (IOException e) {
            log.error("Server error", e);
        } finally {
            context.tickScheduler().stop();
            context.tickRegistry().clear();
        }

    }
}
