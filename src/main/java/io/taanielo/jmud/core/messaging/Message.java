package io.taanielo.jmud.core.messaging;

import java.io.IOException;

public interface Message {
    // TODO tanka 2022-06-20 separation interface
    void send(MessageWriter messageWriter) throws IOException;
}
