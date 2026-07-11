package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code IGNORE} command, which lets a player mute TELL and SAY messages from
 * specific other players.
 *
 * <p>Supported forms:
 * <ul>
 *   <li>{@code IGNORE}                — lists the players currently ignored.</li>
 *   <li>{@code IGNORE ADD <name>}     — adds a player to the ignore list (case-insensitive).</li>
 *   <li>{@code IGNORE REMOVE <name>}  — removes a player from the ignore list.</li>
 *   <li>{@code IGNORE CLEAR}          — clears the entire ignore list.</li>
 * </ul>
 *
 * <p>The ignore relationship is one-directional and silent: an ignored sender receives no
 * indication that their messages were dropped. Logic lives in
 * {@link SocketCommandContext#manageIgnore} so it stays unit-testable without sockets
 * (AGENTS.md §10).
 */
public class IgnoreCommand extends RegistrableCommand {

    /**
     * Creates an {@code IgnoreCommand} and registers it with the given registry.
     *
     * @param registry the registry to register this command with
     */
    public IgnoreCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "ignore";
    }

    @Override
    public String shortDescription() {
        return "Mute TELL and SAY messages from specific players.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: IGNORE  |  IGNORE ADD <name>  |  IGNORE REMOVE <name>  |  IGNORE CLEAR
                 IGNORE               \u2014 list the players you are currently ignoring.
                 IGNORE ADD <name>    \u2014 mute TELL/SAY from the named player.
                 IGNORE REMOVE <name> \u2014 stop ignoring the named player.
                 IGNORE CLEAR         \u2014 clear your entire ignore list.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"IGNORE".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.manageIgnore(args)));
    }
}
