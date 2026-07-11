package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code IDENTIFY <item>} command, revealing a carried item's true rarity tier and stat
 * affixes so that it displays with full coloring and affix labels instead of its generic
 * {@code "an unidentified ..."} name.
 *
 * <p>Accepted tokens: {@code IDENTIFY} and the abbreviation {@code IDENT}.
 */
public class IdentifyCommand extends RegistrableCommand {

    public IdentifyCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "identify";
    }

    @Override
    public String shortDescription() {
        return "Reveal the true nature of an unidentified item you carry.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: IDENTIFY <item>  (alias: IDENT)
                 Studies the named unidentified item in your inventory, revealing its rarity
                 tier and any stat affixes it carries. Already-identified items reveal nothing new.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"IDENTIFY".equals(token) && !"IDENT".equals(token)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.identifyItem(args)));
    }
}
