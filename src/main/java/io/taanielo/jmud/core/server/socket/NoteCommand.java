package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code NOTE} command, letting players post and delete notes on the bulletin board of
 * their current room. Listing notes is done via the companion {@link BoardCommand}.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code NOTE POST <message>}    — pin a new note to the current room's board.</li>
 *   <li>{@code NOTE DELETE <number>}   — remove one of your own notes by its board number.</li>
 * </ul>
 *
 * <p>The game logic lives in {@link SocketCommandContext#manageNote(String)} so it stays
 * unit-testable without sockets (AGENTS.md §10).
 */
public class NoteCommand extends RegistrableCommand {

    /**
     * Creates a {@code NoteCommand} and registers it with the given registry.
     *
     * @param registry the registry to register this command with
     */
    public NoteCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "note";
    }

    @Override
    public String shortDescription() {
        return "Post or delete a note on your current room's bulletin board. Read with BOARD.";
    }

    @Override
    public String longDescription() {
        return "Usage: NOTE POST <message>  |  NOTE DELETE <number>\n"
             + "  NOTE POST <message>   — pin a new note to the board in your current room.\n"
             + "  NOTE DELETE <number>  — remove one of your own notes (numbers come from BOARD).\n"
             + "  Read the current room's board with the BOARD command.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"NOTE".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.manageNote(args)));
    }
}
