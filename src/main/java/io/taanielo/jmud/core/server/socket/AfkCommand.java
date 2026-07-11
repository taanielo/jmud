package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code AFK} command, which toggles the caller's "away from keyboard" status.
 *
 * <p>Supported forms:
 * <ul>
 *   <li>{@code AFK}            — toggles away status on (with a default message) or off.</li>
 *   <li>{@code AFK <message>}  — turns away status on with a custom reason (e.g.
 *       {@code AFK grabbing coffee}).</li>
 * </ul>
 *
 * <p>While away, the player's WHO entry is tagged {@code [AFK]} and anyone who TELLs, WHISPERs, or
 * REPLYs to them additionally learns they are away. The status is per-session only and is cleared
 * automatically by the player's next command (handled in {@link SocketCommandDispatcher}), so it is
 * never persisted. Logic lives in {@link SocketCommandContext#toggleAfk(String)} so it stays
 * unit-testable without sockets (AGENTS.md §10).
 */
public class AfkCommand extends RegistrableCommand {

    /**
     * Creates an {@code AfkCommand} and registers it with the given registry.
     *
     * @param registry the registry to register this command with
     */
    public AfkCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "afk";
    }

    @Override
    public String shortDescription() {
        return "Toggle your away-from-keyboard status.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: AFK  |  AFK <message>
                 AFK            — toggle your away status on (default message) or off.
                 AFK <message>  — mark yourself away with a custom reason.
               Your next command automatically clears the status.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"AFK".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.toggleAfk(args)));
    }
}
