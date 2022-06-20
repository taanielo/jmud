package io.taanielo.jmud;

import java.io.IOException;

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
        // TODO tanka 2022-06-20 separate thread for server
        Server server = new SocketServer(port, clientPool);
        log.info("Starting server ..");
        try {
            server.start();
        } catch (IOException e) {
            log.error("Server error", e);
        }
        log.info("Server closed");
    }
}
