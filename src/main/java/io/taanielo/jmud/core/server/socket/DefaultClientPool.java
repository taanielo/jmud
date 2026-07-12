package io.taanielo.jmud.core.server.socket;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientPool;

/**
 * Default {@link ClientPool}: copy-on-write membership lists mutated from reader threads and
 * iterated as immutable snapshots from the tick thread (AGENTS.md §5). The in-world view is a
 * subset of the connection list, maintained in promotion order; {@link #remove} demotes before it
 * removes so no snapshot can ever show a client as in-world but not connected.
 */
public class DefaultClientPool implements ClientPool {

    private final List<Client> clients = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Client> inWorld = new CopyOnWriteArrayList<>();
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
        inWorld.remove(client);
        clients.remove(client);
    }

    @Override
    public void promoteToWorld(Client client) {
        if (!clients.contains(client)) {
            return;
        }
        inWorld.addIfAbsent(client);
    }

    @Override
    public int getNextId() {
        return nextId.get();
    }

    @Override
    public List<Client> allConnections() {
        return List.copyOf(clients);
    }

    @Override
    public List<Client> inWorld() {
        return List.copyOf(inWorld);
    }
}
