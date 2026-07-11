package io.taanielo.jmud.core.server.socket;

import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.TellService;
import io.taanielo.jmud.core.player.Player;

/**
 * Handles the {@code REPLY} / {@code R} command, a quick shortcut that sends a private message
 * back to whoever most recently sent the current player a {@code TELL} or {@code WHISPER}.
 *
 * <p>Usage: {@code REPLY <message>}
 *
 * <p>Delivery and formatting mirror {@link TellCommand}: the sender sees
 * {@code You tell <Name>: <message>} and the recipient sees {@code <Name> tells you: <message>}.
 * The same ignore-list semantics apply — if the recipient is ignoring the sender, delivery is
 * silently dropped while the sender still sees the normal confirmation.
 *
 * <p>The "last sender" pointer is tracked in {@link TellService} and is purely in-memory: it is
 * updated whenever the player <em>receives</em> a tell/whisper, and does not survive a server
 * restart. Errors are reported when there is no one to reply to, or when the last sender has since
 * gone offline.
 */
public class ReplyCommand extends RegistrableCommand {

    private final TellService tellService;

    /**
     * Creates a {@code ReplyCommand} and registers it with the given registry.
     *
     * @param registry    the registry to register this command with
     * @param tellService service holding the last private-message sender per recipient
     */
    public ReplyCommand(SocketCommandRegistry registry, TellService tellService) {
        super(registry);
        this.tellService = Objects.requireNonNull(tellService, "Tell service is required");
    }

    @Override
    public String name() {
        return "reply";
    }

    @Override
    public String shortDescription() {
        return "Reply privately to the last player who messaged you. Aliases: R";
    }

    @Override
    public String longDescription() {
        return """
               Usage: REPLY <message>
                 Sends a private message to the last player who told or whispered you,
                 so you do not have to retype their name.
                 The recipient sees: <YourName> tells you: <message>
                 Aliases: R\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"REPLY".equals(token) && !"R".equals(token)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> handleReply(context, args)));
    }

    private void handleReply(SocketCommandContext context, String args) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to reply.");
            return;
        }
        if (args.isBlank()) {
            context.writeLineWithPrompt("Usage: REPLY <message>");
            return;
        }
        String message = args.trim();

        Player sender = context.getPlayer();
        String senderName = sender.getUsername().getValue();

        Username lastSender = tellService.lastSender(sender.getUsername()).orElse(null);
        if (lastSender == null) {
            context.writeLineWithPrompt("You have no one to reply to.");
            return;
        }

        // Resolve the last sender among online players (case-insensitive), mirroring TellCommand.
        Username targetUsername = context.onlinePlayerNames().stream()
                .filter(u -> u.equals(lastSender))
                .findFirst()
                .orElse(null);

        if (targetUsername == null) {
            context.writeLineWithPrompt(lastSender.getValue() + " is no longer online.");
            return;
        }

        // Silently drop delivery if the recipient is ignoring the sender; the sender still sees
        // normal confirmation and is never told they have been ignored (issue #339).
        Player targetPlayer = context.getOnlinePlayer(targetUsername);
        boolean ignored = targetPlayer != null && targetPlayer.ignoreList().has(senderName);
        if (!ignored) {
            context.sendToUsername(targetUsername, senderName + " tells you: " + message);
            // The recipient just received a private message; make the sender their REPLY target.
            tellService.recordReceivedTell(targetUsername, sender.getUsername());
        }
        context.writeLineSafe("You tell " + targetUsername.getValue() + ": " + message);
        context.sendPrompt();
    }
}
