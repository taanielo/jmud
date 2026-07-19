package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code SPOUSETELL} / {@code ST} command, which sends a private message to the player's
 * spouse (see the MARRY command) regardless of room or zone.
 *
 * <p>Delivery works like {@code TELL} but is scoped to the bonded partner and is exempt from
 * {@code IGNORE} — spouses cannot mute each other; the escape hatch is {@code MARRY DIVORCE}.
 *
 * <p>This command performs parsing only; delivery runs on the tick thread via
 * {@link SocketCommandContext#spouseTell(String)} (AGENTS.md §5).
 */
public class SpouseTellCommand extends RegistrableCommand {

    /**
     * Creates a {@code SpouseTellCommand} and registers it with the given registry.
     *
     * @param registry the registry this command is part of
     */
    public SpouseTellCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "spousetell";
    }

    @Override
    public String shortDescription() {
        return "Send a private message to your spouse, anywhere. Aliases: ST";
    }

    @Override
    public String longDescription() {
        return """
               Usage: SPOUSETELL <message>
                 Sends a private message to your spouse wherever they are in the world.
                 Your spouse sees: <YourName> tells you (spouse): <message>
                 Unlike TELL, a SPOUSETELL is never blocked by IGNORE. Aliases: ST\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"SPOUSETELL".equals(token) && !"ST".equals(token)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.spouseTell(args)));
    }
}
