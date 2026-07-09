package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;

/**
 * Handles the {@code TELL} / {@code T} command, which sends a private message
 * to another online player regardless of their current room.
 *
 * <p>Usage: {@code TELL <playername> <message>}
 *
 * <p>The sender sees: {@code You tell <Name>: <message>}
 * <br>The recipient sees: {@code <Name> tells you: <message>}
 *
 * <p>Errors are reported to the sender when the target player is not online,
 * or when the command is invoked without sufficient arguments.
 */
public class TellCommand extends RegistrableCommand {

    /**
     * Creates a {@code TellCommand} and registers it with the given registry.
     *
     * @param registry the registry to register this command with
     */
    public TellCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "tell";
    }

    @Override
    public String shortDescription() {
        return "Send a private message to an online player. Aliases: T";
    }

    @Override
    public String longDescription() {
        return "Usage: TELL <playername> <message>\n"
             + "  Sends a private message to the named player, wherever they are.\n"
             + "  The recipient sees: <YourName> tells you: <message>\n"
             + "  Aliases: T";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"TELL".equals(token) && !"T".equals(token)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> handleTell(context, args)));
    }

    private void handleTell(SocketCommandContext context, String args) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to send tells.");
            return;
        }
        if (args.isBlank()) {
            context.writeLineWithPrompt("Usage: TELL <playername> <message>");
            return;
        }
        String[] argParts = args.split("\\s+", 2);
        String targetName = argParts[0];
        if (argParts.length < 2 || argParts[1].isBlank()) {
            context.writeLineWithPrompt("Usage: TELL <playername> <message>");
            return;
        }
        String message = argParts[1].trim();

        Player sender = context.getPlayer();
        String senderName = sender.getUsername().getValue();

        // Find the target among online players (case-insensitive).
        Username targetUsername = context.onlinePlayerNames().stream()
                .filter(u -> u.equals(Username.of(targetName)))
                .findFirst()
                .orElse(null);

        if (targetUsername == null) {
            context.writeLineWithPrompt(targetName + " is not online.");
            return;
        }

        // Silently drop delivery if the recipient is ignoring the sender; the sender still
        // sees normal confirmation and is never told they have been ignored (issue #339).
        Player targetPlayer = context.getOnlinePlayer(targetUsername);
        boolean ignored = targetPlayer != null && targetPlayer.ignoreList().has(senderName);
        if (!ignored) {
            context.sendToUsername(targetUsername, senderName + " tells you: " + message);
        }
        context.writeLineSafe("You tell " + targetUsername.getValue() + ": " + message);
        context.sendPrompt();
    }
}
