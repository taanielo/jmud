package io.taanielo.jmud.core.command;

import io.taanielo.jmud.core.server.Client;

public interface Command {
    void execute(Client client, String... args);
}
