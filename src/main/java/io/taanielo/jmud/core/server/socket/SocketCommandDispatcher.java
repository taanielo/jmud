package io.taanielo.jmud.core.server.socket;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.command.CommandRegistry;
import io.taanielo.jmud.core.world.Direction;

/**
 * Dispatches socket command input to the appropriate action.
 */
public class SocketCommandDispatcher {

    /**
     * Parses the incoming line and executes the appropriate command.
     */
    public void dispatch(SocketCommandContext context, String clientInput) {
        Objects.requireNonNull(context, "Command context is required");
        if (clientInput == null) {
            return;
        }
        String trimmed = clientInput.trim();
        if (trimmed.isEmpty()) {
            context.writeLineWithPrompt("");
            return;
        }
        String[] parts = trimmed.split("\\s+", 2);
        String command = parts[0].toUpperCase(Locale.ROOT);
        String args = parts.length > 1 ? parts[1].trim() : "";

        if (isLookCommand(command)) {
            context.sendLook();
            return;
        }

        Optional<Direction> direction = parseDirection(command, args);
        if (direction.isPresent()) {
            context.sendMove(direction.get());
            return;
        }

        switch (command) {
            case "QUIT" -> CommandRegistry.QUIT.act().input(context);
            case "SAY" -> handleSay(context, args);
            case "CAST", "USE" -> context.useAbility(args);
            case "ANSI" -> context.updateAnsi(args);
            default -> context.writeLineWithPrompt("Unknown command");
        }
    }

    private void handleSay(SocketCommandContext context, String args) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to speak.");
            return;
        }
        if (args.isEmpty()) {
            context.writeLineWithPrompt("Say what?");
            return;
        }
        CommandRegistry.SAY.act().message(context.getPlayer().getUsername(), args, context.clients());
    }

    private boolean isLookCommand(String command) {
        return command.equals("LOOK") || command.equals("L");
    }

    private Optional<Direction> parseDirection(String command, String args) {
        Optional<Direction> direct = Direction.fromInput(command);
        if (direct.isPresent()) {
            return direct;
        }
        if (command.equals("MOVE") || command.equals("GO") || command.equals("WALK")) {
            return Direction.fromInput(args);
        }
        return Optional.empty();
    }
}
