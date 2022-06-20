package io.taanielo.jmud.core.messaging;

import io.taanielo.jmud.core.server.Client;

public interface MessageBroadcaster {
    void broadcast(Client client, Message message);
}
