package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code BOARD} command, listing every note pinned to the player's current room.
 *
 * <p>Each line shows the note's number (for {@code NOTE DELETE}), author, timestamp, and text.
 * Posting and deleting notes is done via the companion {@link NoteCommand}. The game logic lives in
 * {@link SocketCommandContext#showBoard()} so it stays unit-testable without sockets (AGENTS.md §10).
 */
public class BoardCommand extends RegistrableCommand {

    /**
     * Creates a {@code BoardCommand} and registers it with the given registry.
     *
     * @param registry the registry to register this command with
     */
    public BoardCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "board";
    }

    @Override
    public String shortDescription() {
        return "Read the bulletin board of notes posted in your current room. Post with NOTE.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: BOARD
                 BOARD   \u2014 list every note pinned to the board in your current room, showing
                           each note's number, author, timestamp, and text.
                 Post and remove notes with the NOTE command (see HELP NOTE).\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"BOARD".equals(parts[0])) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, SocketCommandContext::showBoard));
    }
}
