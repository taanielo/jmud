package io.taanielo.jmud.core.server.socket;

import java.util.Locale;
import java.util.Optional;

/**
 * Handles the {@code GET <item> FROM <container>} form of the {@code GET} command, which retrieves
 * an item from a container the player is carrying and places it in their inventory.
 *
 * <p>Usage: {@code GET <item> FROM <container>}
 *
 * <p>This command matches only when the input contains a {@code " from "} separator; the plain
 * {@code GET <item>} floor pickup is handled by {@link GetCommand}. The retrieval is delegated to
 * {@code GameActionService.getFromContainer} via
 * {@link SocketCommandContext#getFromContainer(String, String)}.
 */
public class GetFromCommand extends RegistrableCommand {

    static final String SEPARATOR = " from ";

    public GetFromCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "get from";
    }

    @Override
    public String shortDescription() {
        return "Get an item out of a container you are carrying.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: GET <item> FROM <container>
                 Removes the named item from the named container (a bag, chest, or
                 strongbox) you are carrying and adds it to your inventory.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"GET".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        String lower = args.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(SEPARATOR);
        if (idx < 0) {
            return Optional.empty();
        }
        String itemInput = args.substring(0, idx).trim();
        String containerInput = args.substring(idx + SEPARATOR.length()).trim();
        if (itemInput.isEmpty() || containerInput.isEmpty()) {
            return Optional.of(new SocketCommandMatch(
                this, context -> context.writeLineWithPrompt("Usage: GET <item> FROM <container>")));
        }
        return Optional.of(new SocketCommandMatch(
            this, context -> context.getFromContainer(itemInput, containerInput)));
    }
}
