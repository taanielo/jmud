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

    public SocketServer(int port, ClientPool clientPool) {
        this.port = port;
        this.clientPool = clientPool;
        this.context = GameContext.create();
    }

    @Override
    public void run() {
        log.debug("Starting server @ port {}", port);

        context.tickScheduler().start();
        try (ServerSocket server = new ServerSocket(port)) {
            //noinspection InfiniteLoopStatement
            while (true) {
                Socket clientSocket = server.accept();
                try {
                    SocketClient client = new SocketClient(clientSocket, context, clientPool);
                    clientPool.add(client);
                } catch (IOException e) {
                    log.error("Client connecting error", e);
                }
            }
        } catch (IOException e) {
            log.error("Server error", e);
        } finally {
            context.tickScheduler().stop();
            context.tickRegistry().clear();
        }

    }
}
