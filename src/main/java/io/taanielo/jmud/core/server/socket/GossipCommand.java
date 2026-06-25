package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

import io.taanielo.jmud.core.player.Player;

/**
 * Handles the {@code GOSSIP} / {@code GOS} / {@code G} command, which broadcasts
 * a message to all online players regardless of their current room.
 *
 * <p>Usage: {@code GOSSIP <message>}
 *
 * <p>The sender sees: {@code You gossip: <message>}
 * <br>All other online players see: {@code <Name> gossips: <message>}
 *
 * <p>An error is reported to the sender when the command is invoked without a message.
 */
public class GossipCommand extends RegistrableCommand {

    /**
     * Creates a {@code GossipCommand} and registers it with the given registry.
     *
     * @param registry the registry to register this command with
     */
    public GossipCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "gossip";
    }

    @Override
    public String shortDescription() {
        return "Broadcast a message to all online players. Aliases: GOS, G";
    }

    @Override
    public String longDescription() {
        return "Usage: GOSSIP <message>\n"
             + "  Sends a message to every online player, regardless of location.\n"
             + "  Others see: <YourName> gossips: <message>\n"
             + "  Aliases: GOS, G";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"GOSSIP".equals(token) && !"GOS".equals(token) && !"G".equals(token)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> handleGossip(context, args)));
    }

    private void handleGossip(SocketCommandContext context, String message) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to gossip.");
            return;
        }
        if (message == null || message.isBlank()) {
            context.writeLineWithPrompt("Gossip what?");
            return;
        }
        Player sender = context.getPlayer();
        String senderName = sender.getUsername().getValue();
        context.gossip(senderName, message.trim());
        context.writeLineSafe("You gossip: " + message.trim());
        context.sendPrompt();
    }
}
