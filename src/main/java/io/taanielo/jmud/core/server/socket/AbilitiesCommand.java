package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code ABILITIES} / {@code AB} command, which lists every ability
 * the player has learned along with its type, resource cost, and cooldown.
 *
 * <p>Usage: {@code ABILITIES} or {@code AB}
 */
public class AbilitiesCommand extends RegistrableCommand {

    /**
     * Creates an {@code AbilitiesCommand} and registers it with the given registry.
     *
     * @param registry the registry to register this command with
     */
    public AbilitiesCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "abilities";
    }

    @Override
    public String shortDescription() {
        return "List all abilities you have learned, with type, cost, and cooldown. Aliases: AB";
    }

    @Override
    public String longDescription() {
        return "Usage: ABILITIES\n"
             + "  Displays a formatted table of every ability you have learned.\n"
             + "  Each row shows: ability name, type (SKILL/SPELL), resource cost, cooldown.\n"
             + "  Alias: AB";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"ABILITIES".equals(token) && !"AB".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, SocketCommandContext::sendAbilities));
    }
}
