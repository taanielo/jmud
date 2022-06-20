package io.taanielo.jmud.core.messaging;

import static java.util.function.Predicate.not;

import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.server.ClientPool;

public class MessageBroadcasterImpl implements MessageBroadcaster {
    private final ClientPool clientPool;

    public MessageBroadcasterImpl(ClientPool clientPool) {
        this.clientPool = clientPool;
    }

    @Override
    public void broadcast(Client client, Message message) {
        clientPool.clients().stream()
                .filter(not(c -> c.equals(client)))
                .forEach(c -> c.sendMessage(message));
    }
}
