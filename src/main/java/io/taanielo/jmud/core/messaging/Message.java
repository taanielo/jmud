package io.taanielo.jmud.core.messaging;

import java.io.IOException;
import java.io.OutputStream;

public interface Message {
    // TODO tanka 2022-06-20 separation interface
    void send(OutputStream outputStream) throws IOException;
}
