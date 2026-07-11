package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the COMPARE command, showing a side-by-side stat diff between an item and whatever the
 * player currently has equipped in that item's slot.
 *
 * <p>Aliases: {@code CMP}.
 * <p>The command matches the target item the same way {@link ExamineCommand} does: the player's
 * inventory first, then the items on the floor of the current room, with partial name matching.
 */
public class CompareCommand extends RegistrableCommand {

    public CompareCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "compare";
    }

    @Override
    public String shortDescription() {
        return "Compare an item against your equipped gear in that slot. Aliases: CMP";
    }

    @Override
    public String longDescription() {
        return """
               Usage: COMPARE <item>  |  CMP <item>
                 Shows a side-by-side comparison of an item in your inventory or on the floor of
                 the current room against whatever you currently have equipped in that item's slot,
                 with a per-stat delta plus weight, value, and durability. Use it to check whether a
                 piece of loot is an upgrade before committing to a swap.
                 Partial name matching is supported (e.g. COMPARE ir matches Iron Sword).\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if ("COMPARE".equals(token) || "CMP".equals(token)) {
            String args = parts[1];
            return Optional.of(new SocketCommandMatch(this, context -> context.compareItem(args)));
        }
        return Optional.empty();
    }
}
