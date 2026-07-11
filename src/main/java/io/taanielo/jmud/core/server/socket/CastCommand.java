package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code CAST} command, which activates only spell-type abilities.
 *
 * <p>Unlike {@link AbilityCommand} ({@code USE}), this command restricts activation to
 * abilities whose {@link io.taanielo.jmud.core.ability.AbilityType} is {@code SPELL}.
 * Attempting to cast a skill-type ability prints a clear error message.
 */
public class CastCommand extends RegistrableCommand {

    /**
     * Creates a {@code CastCommand} and registers it with the given registry.
     *
     * @param registry the registry to register this command with
     */
    public CastCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "cast";
    }

    @Override
    public String shortDescription() {
        return "Cast a learned spell. Usage: CAST <spell> [target]";
    }

    @Override
    public String longDescription() {
        return """
               Usage: CAST <spell-name> [target]
                 Activates a learned spell-type ability. Skills cannot be used with CAST.
                 Examples:
                   CAST fireball              \u2014 cast Fireball on your current target
                   CAST heal self             \u2014 cast Heal on yourself
                   CAST lightning bolt goblin \u2014 cast Lightning Bolt at the goblin
                 To use skill-type abilities, use the USE command instead.
                 See also: USE (all abilities), ABILITIES (list all known abilities)\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"CAST".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.castSpell(args)));
    }
}
