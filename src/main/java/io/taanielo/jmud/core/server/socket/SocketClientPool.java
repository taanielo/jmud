package io.taanielo.jmud.core.server.socket;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientPool;

public class SocketClientPool implements ClientPool {

    private final List<SocketClient> clients = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger();

    @Override
    public void add(Client client) {
        if (client instanceof SocketClient socketClient) {
            int clientId = nextId.getAndIncrement();
            clients.add(socketClient);
            Thread.ofVirtual()
                .name("client-" + clientId)
                .start(socketClient);
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
        return nextId.get();
    }

    @Override
    public List<Client> clients() {
        return clients.stream()
                .map(c -> (Client)c)
                .toList();
    }
}
