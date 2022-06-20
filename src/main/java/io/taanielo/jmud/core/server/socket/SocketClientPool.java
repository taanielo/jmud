package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.List;

import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientPool;

public class SocketClientPool implements ClientPool {

    private final List<SocketClient> clients = new ArrayList<>();

    @Override
    public void add(Client client) {
        if (client instanceof SocketClient socketClient) {
            Thread thread = new Thread(socketClient, "client-" + clients.size());
            clients.add(socketClient);
            thread.start();
        }
    }

    @Override
    public void remove(Client client) {
        if (client instanceof SocketClient socketClient) {
            clients.remove(socketClient);
        }
    }

    @Override
    public int getNextId() {
        return clients.size();
    }

    @Override
    public List<Client> clients() {
        return clients.stream()
                .map(c -> (Client)c)
                .toList();
    }
}
