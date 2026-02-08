package io.taanielo.jmud.core.server.socket;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientPool;

public class DefaultClientPool implements ClientPool {

    private final List<Client> clients = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger();

    @Override
    public void add(Client client) {
        int clientId = nextId.getAndIncrement();
        clients.add(client);
        Thread.ofVirtual()
            .name("client-" + clientId)
            .start(client);
    }

    @Override
    public void remove(Client client) {
        clients.remove(client);
    }

    @Override
    public int getNextId() {
        return nextId.get();
    }

    @Override
    public List<Client> clients() {
        return List.copyOf(clients);
    }
}
