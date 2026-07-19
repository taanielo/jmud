package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code EFFECTS} / {@code AFFECTS} command, which lists the player's active status
 * effects (buffs, debuffs, damage-over-time and heal-over-time) split into beneficial and harmful
 * groups, with each effect's remaining duration and stack count.
 *
 * <p>Usage: {@code EFFECTS} or {@code AFFECTS}
 */
public class EffectsCommand extends RegistrableCommand {

    /**
     * Creates an {@code EffectsCommand} and registers it with the given registry.
     *
     * @param registry the registry to register this command with
     */
    public EffectsCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "effects";
    }

    @Override
    public String shortDescription() {
        return "List your active buffs, debuffs, and DoTs with remaining duration. Aliases: AFFECTS";
    }

    @Override
    public String longDescription() {
        return """
               Usage: EFFECTS
                 Lists every status effect currently affecting you, split into
                 beneficial and harmful groups. Each line shows the effect name,
                 its remaining duration in ticks (or 'permanent'), and a stack
                 count when the effect is stacked.
                 Alias: AFFECTS\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"EFFECTS".equals(token) && !"AFFECTS".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, SocketCommandContext::sendEffects));
    }
}
