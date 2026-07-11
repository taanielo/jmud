package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles AUTOLOOT toggle commands ({@code AUTOLOOT ON|OFF|TOGGLE|STATUS}).
 *
 * <p>When enabled, items dropped by mobs the player kills solo (outside round-robin party loot mode)
 * are placed straight into the player's inventory instead of onto the room floor, subject to carry
 * capacity. Modeled on {@link AnsiCommand}.
 */
public class AutoLootCommand extends RegistrableCommand {
    public AutoLootCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "autoloot";
    }

    @Override
    public String shortDescription() {
        return "Toggle automatically looting items from your solo kills.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: AUTOLOOT [on|off|toggle|status]
                 AUTOLOOT          — show whether autoloot is currently ON or OFF.
                 AUTOLOOT ON       — place items you loot from solo kills straight into your inventory.
                 AUTOLOOT OFF      — leave dropped items on the room floor (the default).
                 AUTOLOOT TOGGLE   — flip the current setting.
               Items you cannot carry still drop to the floor, and party round-robin loot is unaffected.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"AUTOLOOT".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.updateAutoLoot(args)));
    }
}
