package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code SNEAK} command and its {@code HIDE} alias, the rogue-only stealth toggle.
 *
 * <p>Toggling stealth on hides the rogue so aggressive mobs will not engage them for the first
 * time; toggling it off (or attacking / using an ability) reveals them again. The game logic lives
 * in {@code GameActionService.sneakToggle} via {@link SocketCommandContext#sneak(String)}.
 */
public class SneakCommand extends RegistrableCommand {

    public SneakCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "sneak";
    }

    @Override
    public String shortDescription() {
        return "Slip into (or out of) the shadows (rogue skill only).";
    }

    @Override
    public String longDescription() {
        return "Usage: SNEAK | HIDE\n"
             + "  Toggles stealth. While hidden, aggressive mobs will not engage you, and a\n"
             + "  BACKSTAB opened from the shadows strikes for bonus damage. Attacking or using\n"
             + "  an ability reveals you. Rogues only.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"SNEAK".equals(token) && !"HIDE".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, context -> context.sneak("")));
    }
}
