package io.taanielo.jmud.command;

import java.util.List;

import lombok.Value;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.server.Client;

@Value(staticConstructor = "of")
public class CommandData {
    Client client;
    Username username;
    List<Client> targetClients;
    String input;
}