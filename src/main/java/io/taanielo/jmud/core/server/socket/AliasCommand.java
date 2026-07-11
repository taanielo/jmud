package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code ALIAS} command, which lets a player bind a short custom string to a
 * longer command line.
 *
 * <p>Three forms are supported:
 * <ul>
 *   <li>{@code ALIAS}                    — lists the player's currently defined aliases.</li>
 *   <li>{@code ALIAS -d <name>}          — removes the named alias.</li>
 *   <li>{@code ALIAS <name> <expansion>} — defines or overwrites the named alias.</li>
 * </ul>
 *
 * <p>Once defined, typing {@code <name>} as the first word of a subsequent command line
 * expands to {@code <expansion>} before normal command matching; see
 * {@link SocketCommandDispatcher}. Logic lives in {@link SocketCommandContext#manageAlias}
 * so it stays unit-testable without sockets (AGENTS.md §10).
 */
public class AliasCommand extends RegistrableCommand {

    public AliasCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "alias";
    }

    @Override
    public String shortDescription() {
        return "Define, list, or remove custom command aliases.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: ALIAS  |  ALIAS <name> <expansion>  |  ALIAS -d <name>
                 ALIAS                    \u2014 list your currently defined aliases.
                 ALIAS <name> <expansion> \u2014 define or overwrite an alias.
                 ALIAS -d <name>          \u2014 remove an alias.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"ALIAS".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.manageAlias(args)));
    }
}
