package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the EXAMINE command, letting players read an item's full description,
 * equipment slot, and stat bonuses.
 *
 * <p>Aliases: {@code EX}, {@code EXAM}.
 * <p>The command searches the player's inventory first, then the items on the
 * floor of the current room.
 */
public class ExamineCommand extends RegistrableCommand {

    public ExamineCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "examine";
    }

    @Override
    public String shortDescription() {
        return "Read the description of an item in your inventory or room. Aliases: EX, EXAM";
    }

    @Override
    public String longDescription() {
        return "Usage: EXAMINE <item>  |  EX <item>  |  EXAM <item>\n"
             + "  Displays the full description, equipment slot (if any), and stat bonuses\n"
             + "  for an item found in your inventory or on the floor of the current room.\n"
             + "  Partial name matching is supported (e.g. EXAMINE ir matches Iron Sword).";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if ("EXAMINE".equals(token) || "EX".equals(token) || "EXAM".equals(token)) {
            String args = parts[1];
            return Optional.of(new SocketCommandMatch(this, context -> context.examineItem(args)));
        }
        return Optional.empty();
    }
}
