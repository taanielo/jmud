package io.taanielo.jmud.core.server.socket;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.messaging.MessageWriter;
import io.taanielo.jmud.core.server.connection.ClientConnection;

/**
 * Telnet-backed connection implementation.
 */
public class TelnetClientConnection implements ClientConnection {

    private final TelnetConnection connection;
    private TelnetLineReader lineReader;

    public TelnetClientConnection(java.net.Socket socket) {
        this.connection = new TelnetConnection(Objects.requireNonNull(socket, "Socket is required"));
    }

    @Override
    public void open() throws IOException {
        connection.open();
        this.lineReader = new TelnetLineReader(connection.input());
    }

    @Override
    public String readLine() throws IOException {
        while (true) {
            TelnetLineReader.Result result = lineReader.readLine();
            switch (result) {
                case TelnetLineReader.Result.EndOfStream ignored -> {
                    return null;
                }
                case TelnetLineReader.Result.Oversized ignored ->
                    connection.writeLine("*** Line too long; input discarded. ***");
                case TelnetLineReader.Result.Line line -> {
                    return line.text();
                }
            }
        }
    }

    @Override
    public MessageWriter messageWriter() {
        return connection.messageWriter();
    }

    @Override
    public void sendMessage(io.taanielo.jmud.core.messaging.Message message) throws IOException {
        connection.sendMessage(message);
    }

    @Override
    public void writeLine(String message) {
        connection.writeLine(message);
    }

    @Override
    public void writeLines(List<String> lines) {
        connection.writeLines(lines);
    }

    @Override
    public void write(String text) {
        connection.write(text);
    }

    @Override
    public void close() {
        connection.close();
    }
}
