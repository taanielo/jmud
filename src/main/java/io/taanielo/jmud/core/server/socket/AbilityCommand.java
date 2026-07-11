package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code USE} command, which activates any learned ability regardless of type
 * (both {@code SKILL} and {@code SPELL}).
 *
 * <p>For spell-only activation with type enforcement, see {@link CastCommand}.
 */
public class AbilityCommand extends RegistrableCommand {

    /**
     * Creates an {@code AbilityCommand} and registers it with the given registry.
     *
     * @param registry the registry to register this command with
     */
    public AbilityCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "use";
    }

    @Override
    public String shortDescription() {
        return "Activate a learned ability (skill or spell). Usage: USE <ability> [target]";
    }

    @Override
    public String longDescription() {
        return """
               Usage: USE <ability-name> [target]
                 Activates any learned ability, regardless of whether it is a skill or a spell.
                 Examples:
                   USE bash               \u2014 use the Bash skill on your current target
                   USE fireball goblin    \u2014 cast Fireball at the goblin
                 See also: CAST (spell-only), ABILITIES (list all known abilities)\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"USE".equals(token)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.useAbility(args)));
    }
}
