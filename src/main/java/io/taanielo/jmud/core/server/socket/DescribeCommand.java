package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code DESCRIBE} command (alias {@code DESC}), letting a player set a custom roleplay
 * description that other players see when they {@code LOOK} at them.
 *
 * <p>Forms:
 * <ul>
 *   <li>{@code DESCRIBE}          — show the current custom description, or a hint when none is set.</li>
 *   <li>{@code DESCRIBE <text>}   — set the custom description (free text, capped at 240 characters).</li>
 *   <li>{@code DESCRIBE CLEAR}    — clear the custom description (also {@code DESCRIBE NONE}).</li>
 *   <li>{@code DESCRIBE <pet>}          — show one of your tamed companions' description (owner only).</li>
 *   <li>{@code DESCRIBE <pet> <text>}   — set a companion's roleplay description (also 240-char cap).</li>
 *   <li>{@code DESCRIBE <pet> CLEAR}    — clear a companion's description (also {@code NONE}).</li>
 * </ul>
 *
 * <p>Game logic lives in {@link SocketCommandContext#manageDescription(String)} so it stays
 * unit-testable without sockets (AGENTS.md §10). When the leading token names one of the player's own
 * tamed companions, the command targets that companion (matched the same way {@code NAME} matches);
 * otherwise it manages the player's own description. Both are cosmetic-only state persisted on the
 * player record; neither interacts with combat, economy, or death flows.
 */
public class DescribeCommand extends RegistrableCommand {

    /**
     * Creates a {@code DescribeCommand} and registers it with the given registry.
     *
     * @param registry the registry this command is part of
     */
    public DescribeCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "describe";
    }

    @Override
    public String shortDescription() {
        return "Set the custom description others see when they LOOK at you.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: DESCRIBE [<pet>] [<text> | CLEAR]
                 DESCRIBE                — show your current custom description (alias DESC).
                 DESCRIBE <text>         — set your description (free text, max 240 characters).
                 DESCRIBE CLEAR          — clear it, reverting to the default line (also DESCRIBE NONE).
                 DESCRIBE <pet>          — show one of your tamed companions' description.
                 DESCRIBE <pet> <text>   — set that companion's description (max 240 characters).
                 DESCRIBE <pet> CLEAR    — clear that companion's description (also NONE).
                 Match the companion by its kind or its current name (see NAME); only your own
                 companions can be described, and the description shows when anyone LOOKs at the pet.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"DESCRIBE".equals(parts[0]) && !"DESC".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.manageDescription(args)));
    }
}
