package io.taanielo.jmud.core.messaging;

import java.io.IOException;

public interface MessageWriter {
    void write(String message) throws IOException;

    default void writeLine(String message) throws IOException {
        write(message + "\r\n");
    }

    default void writeLine() throws IOException {
        write("\r\n");
    }

}
