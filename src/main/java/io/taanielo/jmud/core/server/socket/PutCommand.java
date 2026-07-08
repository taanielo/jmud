package io.taanielo.jmud.core.server.socket;

import java.util.Locale;
import java.util.Optional;

/**
 * Handles the {@code PUT} command, which places an item from the player's inventory into a
 * container the player is carrying.
 *
 * <p>Usage: {@code PUT <item> <into|in> <container>}
 *
 * <p>The item transfer (capacity validation, unequipping if worn, moving the item into the
 * container's contents) is delegated to {@code GameActionService.putItem} via
 * {@link SocketCommandContext#putIntoContainer(String, String)}.
 */
public class PutCommand extends RegistrableCommand {

    public PutCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "put";
    }

    @Override
    public String shortDescription() {
        return "Put an item from your inventory into a container.";
    }

    @Override
    public String longDescription() {
        return "Usage: PUT <item> <into|in> <container>\n"
             + "  Moves the named item from your inventory into the named container\n"
             + "  (a bag, chest, or strongbox) you are carrying, if it has room.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"PUT".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        String[] split = splitOnSeparator(args);
        if (split == null) {
            return Optional.of(new SocketCommandMatch(
                this, context -> context.writeLineWithPrompt("Usage: PUT <item> <into|in> <container>")));
        }
        String itemInput = split[0];
        String containerInput = split[1];
        return Optional.of(new SocketCommandMatch(
            this, context -> context.putIntoContainer(itemInput, containerInput)));
    }

    /**
     * Splits {@code args} into {@code [item, container]} around the first {@code " into "} or
     * {@code " in "} separator (case-insensitive), or {@code null} when neither is present or
     * either side is blank.
     */
    private static String[] splitOnSeparator(String args) {
        String[] byInto = splitAround(args, " into ");
        if (byInto != null) {
            return byInto;
        }
        return splitAround(args, " in ");
    }

    private static String[] splitAround(String args, String separator) {
        String lower = args.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(separator);
        if (idx < 0) {
            return null;
        }
        String item = args.substring(0, idx).trim();
        String container = args.substring(idx + separator.length()).trim();
        if (item.isEmpty() || container.isEmpty()) {
            return null;
        }
        return new String[] {item, container};
    }
}
