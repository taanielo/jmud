package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.MessageBroadcasterImpl;
import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.Server;

@Slf4j
public class SocketServer implements Server {
    private final int port;

    private final ClientPool clientPool;
    private final MessageBroadcaster messageBroadCaster;

    public SocketServer(int port, ClientPool clientPool) {
        this.port = port;
        this.clientPool = clientPool;
        this.messageBroadCaster = new MessageBroadcasterImpl(clientPool);
    }

    @Override
    public void start() throws IOException {
        log.debug("Starting server @ port {}", port);

        try (ServerSocket server = new ServerSocket(port)) {
            //noinspection InfiniteLoopStatement
            while (true) {
                Socket clientSocket = server.accept();
                try {
                    SocketClient client = new SocketClient(clientSocket, messageBroadCaster);
                    clientPool.add(client);
                } catch (IOException e) {
                    log.error("Client connecting error", e);
                }
            }
        }

    }
}
