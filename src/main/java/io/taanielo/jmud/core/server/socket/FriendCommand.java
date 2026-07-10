package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code FRIEND} command, which lets a player maintain a persistent buddy list that is
 * highlighted in {@code WHO}.
 *
 * <p>Supported forms:
 * <ul>
 *   <li>{@code FRIEND}                — lists the player's friends and their online status.</li>
 *   <li>{@code FRIEND LIST}           — same as {@code FRIEND}.</li>
 *   <li>{@code FRIEND ADD <name>}     — adds a player to the friends list (case-insensitive).</li>
 *   <li>{@code FRIEND REMOVE <name>}  — removes a player from the friends list.</li>
 *   <li>{@code FRIEND CLEAR}          — clears the entire friends list.</li>
 * </ul>
 *
 * <p>The friend relationship is one-directional and silent: the befriended player receives no
 * notification. Logic lives in {@link SocketCommandContext#manageFriends} so it stays
 * unit-testable without sockets (AGENTS.md §10).
 */
public class FriendCommand extends RegistrableCommand {

    /**
     * Creates a {@code FriendCommand} and registers it with the given registry.
     *
     * @param registry the registry to register this command with
     */
    public FriendCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "friend";
    }

    @Override
    public String shortDescription() {
        return "Maintain a persistent friends list, highlighted in WHO.";
    }

    @Override
    public String longDescription() {
        return "Usage: FRIEND  |  FRIEND ADD <name>  |  FRIEND REMOVE <name>  |  FRIEND CLEAR\n"
             + "  FRIEND               — list your friends and who is online.\n"
             + "  FRIEND ADD <name>    — add the named player to your friends list.\n"
             + "  FRIEND REMOVE <name> — remove the named player from your friends list.\n"
             + "  FRIEND CLEAR         — clear your entire friends list.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"FRIEND".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.manageFriends(args)));
    }
}
