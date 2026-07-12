package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code LFG} command, which toggles the caller's "looking for group" status (issue
 * #510) so other players can spot them in the {@code WHO} roster.
 *
 * <p>Supported forms:
 * <ul>
 *   <li>{@code LFG}           — toggles LFG status on (with a default marker) or off.</li>
 *   <li>{@code LFG <note>}    — turns LFG status on with a custom note (e.g.
 *       {@code LFG tank for Catacombs}).</li>
 *   <li>{@code LFG STATUS}    — reports the caller's current LFG state and note without toggling.</li>
 * </ul>
 *
 * <p>While LFG, the player's WHO entry is tagged {@code [LFG]} (with the note, if any). Unlike AFK,
 * the status is not cleared by the player's next command — it stays on until toggled off. It is
 * per-session only and never persisted, so it resets on logout/disconnect. Logic lives in
 * {@link SocketCommandContext#toggleLfg(String)} so it stays unit-testable without sockets
 * (AGENTS.md §10).
 */
public class LfgCommand extends RegistrableCommand {

    /**
     * Creates an {@code LfgCommand} and registers it with the given registry.
     *
     * @param registry the registry to register this command with
     */
    public LfgCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "lfg";
    }

    @Override
    public String shortDescription() {
        return "Toggle your looking-for-group status.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: LFG  |  LFG <note>  |  LFG STATUS
                 LFG           — toggle your looking-for-group status on (default) or off.
                 LFG <note>    — flag yourself LFG with a note, e.g. LFG tank for Catacombs.
                 LFG STATUS    — report your current LFG state and note.
               While LFG your WHO entry is tagged [LFG]; the flag stays on until you toggle it off\
                and clears on disconnect.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"LFG".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.toggleLfg(args)));
    }
}
