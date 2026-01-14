package io.taanielo.jmud.core.command;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.taanielo.jmud.core.server.Client;

public class CommandHandler {
    private final Map<String, Command> commands = new HashMap<>();

    public void register(String name, Command command) {
        commands.put(name, command);
    }

    public void handle(Client client, String line) {
        String[] parts = line.split(" ");
        String commandName = parts[0];
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        Command command = commands.get(commandName);
        if (command != null) {
            command.execute(client, args);
        } else {
            // Handle unknown command
        }
    }
}
