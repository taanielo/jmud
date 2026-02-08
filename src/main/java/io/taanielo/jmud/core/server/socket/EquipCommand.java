package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles equip commands.
 */
public class EquipCommand extends RegistrableCommand {
    public EquipCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "equip";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"EQUIP".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.equipItem(args)));
    }
}
