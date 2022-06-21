package io.taanielo.jmud;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.server.ClientPool;
import io.taanielo.jmud.core.server.Server;
import io.taanielo.jmud.core.server.socket.SocketClientPool;
import io.taanielo.jmud.core.server.socket.SocketServer;

@Slf4j
public class Main {

    public static void main(String[] args) {
        int port = 4444;
        ClientPool clientPool = new SocketClientPool();
        Server server = new SocketServer(port, clientPool);
        log.info("Starting server ..");
        Thread serverThread = new Thread(server, "socket-server");
        serverThread.start();
        log.info("Server closed");
    }
}
