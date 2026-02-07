package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

import io.taanielo.jmud.core.player.Player;

/**
 * Handles say commands.
 */
public class SayCommand extends RegistrableCommand {
    public SayCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "say";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"SAY".equals(parts[0])) {
            return Optional.empty();
        }
        String message = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> handleSay(context, message)));
    }

    private void handleSay(SocketCommandContext context, String message) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to speak.");
            return;
        }
        if (message.isEmpty()) {
            context.writeLineWithPrompt("Say what?");
            return;
        }
        Player player = context.getPlayer();
        String roomMessage = player.getUsername().getValue() + " said \"" + message + "\"";
        context.sendToRoom(player, roomMessage);
        context.writeLineSafe("You say \"" + message + "\"");
        context.sendPrompt();
    }
}
