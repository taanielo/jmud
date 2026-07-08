package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code REPAIR <item>} command, restoring a damaged piece of gear to full durability
 * for a gold fee, provided a blacksmith is present in the current room.
 */
public class RepairCommand extends RegistrableCommand {

    public RepairCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "repair";
    }

    @Override
    public String shortDescription() {
        return "Have a blacksmith repair a damaged item.";
    }

    @Override
    public String longDescription() {
        return "Usage: REPAIR <item name>\n"
             + "  Asks the blacksmith in the current room to restore the named item to full\n"
             + "  durability. The cost scales with the item's value and how damaged it is. You\n"
             + "  must be carrying the item and have enough gold.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"REPAIR".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.repairItem(args)));
    }
}
