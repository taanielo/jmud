package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code QUEST} command, letting players interact with available
 * quest contracts via the Guild Clerk in the Courtyard.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code QUEST LIST}           — shows available contracts</li>
 *   <li>{@code QUEST LOG}            — lists the player's completed one-time contracts</li>
 *   <li>{@code QUEST ACCEPT <name>}  — accepts the named contract</li>
 *   <li>{@code QUEST STATUS}         — prints current quest progress (including explored rooms)</li>
 *   <li>{@code QUEST TRACK}          — points you toward the nearest live target of your kill quest</li>
 *   <li>{@code QUEST COMPLETE}       — claims kill-quest reward (must be in Courtyard)</li>
 *   <li>{@code QUEST DELIVER}        — turns in collected items to the Guild Clerk, or hands a package to the receiving NPC</li>
 *   <li>{@code QUEST ABANDON}        — drops the active quest with no penalty</li>
 * </ul>
 */
public class QuestCommand extends RegistrableCommand {

    public QuestCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "quest";
    }

    @Override
    public String shortDescription() {
        return "Interact with quest contracts from the Guild Clerk. Use QUEST LIST to begin.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: QUEST <sub-command> [args]
                 QUEST LIST             \u2014 show available contracts
                 QUEST LOG              \u2014 list your completed one-time contracts
                 QUEST ACCEPT <id>      \u2014 accept a contract (e.g. QUEST ACCEPT rat-catcher)
                 QUEST STATUS           \u2014 show current quest progress
                 QUEST TRACK            \u2014 point toward the nearest live target of your kill quest
                 QUEST COMPLETE         \u2014 claim kill-quest reward (must be in the Courtyard)
                 QUEST DELIVER          \u2014 turn in collected items to the Guild Clerk, or hand a package to the receiving NPC
                 QUEST ABANDON          \u2014 drop the active quest with no penalty\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"QUEST".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.executeQuest(args)));
    }
}
