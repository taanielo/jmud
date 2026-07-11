package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code TITLE} command, letting a player choose which of their earned titles is
 * displayed next to their name (in {@code WHO}) and marked active (in {@code SCORE}).
 *
 * <p>Forms:
 * <ul>
 *   <li>{@code TITLE}         — list every earned title, marking the active one (or "none").</li>
 *   <li>{@code TITLE <name>}  — set the active title (case-insensitive) if it has been earned.</li>
 *   <li>{@code TITLE NONE}    — clear the active title (also {@code TITLE CLEAR}).</li>
 * </ul>
 *
 * <p>Game logic lives in {@link SocketCommandContext#manageTitle(String)} so it stays
 * unit-testable without sockets (AGENTS.md §10). Titles are earned elsewhere (e.g. quest
 * completion); this command only selects among them.
 */
public class TitleCommand extends RegistrableCommand {

    /**
     * Creates a {@code TitleCommand} and registers it with the given registry.
     *
     * @param registry the registry this command is part of
     */
    public TitleCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "title";
    }

    @Override
    public String shortDescription() {
        return "Choose which earned title to display next to your name.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: TITLE  |  TITLE <name>  |  TITLE NONE
                 TITLE          \u2014 list your earned titles and show which is active.
                 TITLE <name>   \u2014 display an earned title (case-insensitive).
                 TITLE NONE     \u2014 stop displaying a title (also TITLE CLEAR).\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"TITLE".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.manageTitle(args)));
    }
}
