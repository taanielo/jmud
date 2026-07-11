package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code SALVAGE} command. With no arguments it lists the carried, unequipped weapon and
 * armor items that can be broken down, previewing the material(s) each would yield; with an item
 * argument it destroys that carried item and adds the yielded materials to the player's inventory.
 * Requires a blacksmith to be present in the current room, mirroring {@link CraftCommand} and
 * {@link RepairCommand}.
 */
public class SalvageCommand extends RegistrableCommand {

    public SalvageCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "salvage";
    }

    @Override
    public String shortDescription() {
        return "Break unwanted gear down into crafting materials at a blacksmith.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: SALVAGE [item name]
                 With no argument, lists the weapons and armor you are carrying that can be broken
                 down, showing the materials each would yield.
                 With an item name, salvages it: the item is destroyed and materials based on its
                 rarity are added to your inventory. Better gear yields more or rarer materials.
                 You must be standing near a blacksmith, and the item must be unequipped (UNEQUIP
                 it first). Quest items and non-gear (potions, food, keys) cannot be salvaged.
                 Salvaging is irreversible.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"SALVAGE".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.salvage(args)));
    }
}
