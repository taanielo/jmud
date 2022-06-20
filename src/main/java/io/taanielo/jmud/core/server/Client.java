package io.taanielo.jmud.core.server;

import io.taanielo.jmud.core.messaging.Message;

public interface Client extends Runnable {

    void sendMessage(Message message);

    void close();
}
