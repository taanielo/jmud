package io.taanielo.jmud.core.command;

import io.taanielo.jmud.core.server.Client;

public class QuitCommand implements Command {
    @Override
    public void execute(Client client, String... args) {
        client.close();
    }
}
