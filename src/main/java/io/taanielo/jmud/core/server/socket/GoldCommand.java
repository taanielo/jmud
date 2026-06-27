package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

import io.taanielo.jmud.core.player.Player;

/**
 * Handles the {@code GOLD} command, displaying the player's current gold balance.
 */
public class GoldCommand extends RegistrableCommand {

    public GoldCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "gold";
    }

    @Override
    public String shortDescription() {
        return "Display your current gold balance.";
    }

    @Override
    public String longDescription() {
        return "Usage: GOLD\n"
             + "  Shows how many gold coins you are currently carrying.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"GOLD".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, GoldCommand::handleGold));
    }

    private static void handleGold(SocketCommandContext context) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to check your gold.");
            return;
        }
        Player player = context.getPlayer();
        context.writeLineWithPrompt("You are carrying " + player.getGold() + " gold coin"
            + (player.getGold() == 1 ? "" : "s") + ".");
    }
}
