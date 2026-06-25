package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code equipment} / {@code eq} command, displaying the items a
 * player currently has worn in each equipment slot.
 */
public class EquipmentCommand extends RegistrableCommand {

    public EquipmentCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "equipment";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"EQUIPMENT".equals(token) && !"EQ".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, EquipmentCommand::handleEquipment));
    }

    private static void handleEquipment(SocketCommandContext context) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to check your equipment.");
            return;
        }
        context.sendEquipment();
    }
}
