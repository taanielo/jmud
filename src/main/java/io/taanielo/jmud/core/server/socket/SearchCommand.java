package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code SEARCH} command, which hunts the current room for secret exits.
 *
 * <p>SEARCH takes no arguments and is usable by any class at any level. Each attempt has a chance to
 * uncover any undiscovered hidden exit in the room; once found, that exit becomes a normal, visible,
 * walkable exit for every player. A miss costs nothing but the attempt, so repeated searching is
 * fine. The game logic lives in {@code GameActionService.searchForHiddenExits} via
 * {@link SocketCommandContext#searchRoom()}.
 */
public class SearchCommand extends RegistrableCommand {

    public SearchCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "search";
    }

    @Override
    public String shortDescription() {
        return "Search the room for hidden exits.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: SEARCH
                 Carefully searches your current room for secret passages. Each attempt has a
                 chance to reveal a hidden exit; once found, it becomes a normal exit for everyone.
                 A search that finds nothing costs you nothing, so feel free to try again. You
                 cannot search while fighting, and searching reveals you if you were hidden.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String token = SocketCommandParsing.firstToken(input);
        if (!"SEARCH".equals(token)) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, SocketCommandContext::searchRoom));
    }
}
