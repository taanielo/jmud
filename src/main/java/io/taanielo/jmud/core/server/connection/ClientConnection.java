package io.taanielo.jmud.core.server.connection;

import java.io.IOException;
import java.util.List;

import io.taanielo.jmud.core.messaging.MessageWriter;

/**
 * Transport-agnostic connection for client I/O.
 */
public interface ClientConnection {

    /**
     * Opens the underlying connection resources.
     *
     * @throws IOException if the connection cannot be opened
     */
    void open() throws IOException;

    /**
     * Reads a single line of input or returns null on EOF.
     *
     * @throws IOException if reading fails
     */
    String readLine() throws IOException;

    /**
     * Returns the message writer for structured output.
     */
    MessageWriter messageWriter();

    /**
     * Sends a structured message through the writer.
     *
     * @throws IOException if sending fails
     */
    void sendMessage(io.taanielo.jmud.core.messaging.Message message) throws IOException;

    /**
     * Writes a line of text followed by CR+LF.
     */
    void writeLine(String message);

    /**
     * Writes multiple lines, each followed by CR+LF.
     */
    void writeLines(List<String> lines);

    /**
     * Writes raw text without a trailing newline.
     */
    void write(String text);

    /**
     * Closes the connection.
     */
    void close();
}
