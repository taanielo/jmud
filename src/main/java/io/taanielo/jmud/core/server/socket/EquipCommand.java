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
    public String shortDescription() {
        return "Equip an item from your inventory. Aliases: WIELD, WEAR, HOLD";
    }

    @Override
    public String longDescription() {
        return """
               Usage: EQUIP <item>
                 Equips a weapon, armour, or other wearable item from your inventory; the
                 slot is resolved automatically from the item. Aliases: WIELD, WEAR, HOLD.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String verb = parts[0];
        if (!"EQUIP".equals(verb) && !"WIELD".equals(verb) && !"WEAR".equals(verb) && !"HOLD".equals(verb)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.equipItem(args)));
    }
}
