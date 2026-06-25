package io.taanielo.jmud.core.server.socket;

import java.util.List;
import java.util.Optional;

import io.taanielo.jmud.core.authentication.Username;

/**
 * Handles the {@code who} command, listing all connected and authenticated
 * players together with a total online count.
 *
 * <p>The command is read-only: it inspects live session state via the context
 * and never reads or writes persisted data. Formatting is delegated to
 * {@link WhoListing} so the listing logic stays testable without networking.
 */
public class WhoCommand extends RegistrableCommand {
    public WhoCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "who";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"WHO".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, WhoCommand::handleWho));
    }

    private static void handleWho(SocketCommandContext context) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to see who is online.");
            return;
        }
        List<Username> onlineNames = context.onlinePlayerNames();
        for (String line : WhoListing.format(onlineNames)) {
            context.writeLineSafe(line);
        }
        context.sendPrompt();
    }
}
