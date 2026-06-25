package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code inventory} / {@code inv} / {@code i} command, displaying
 * the items a player is currently carrying together with individual weights and
 * total encumbrance.
 */
public class InventoryCommand extends RegistrableCommand {

    public InventoryCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "inventory";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"INVENTORY".equals(token) && !"INV".equals(token) && !"I".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, InventoryCommand::handleInventory));
    }

    private static void handleInventory(SocketCommandContext context) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to check your inventory.");
            return;
        }
        context.sendInventory();
    }
}
