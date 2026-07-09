package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code DAILY_QUEST} command, letting players interact with the rotating daily quest
 * pools that reset each game day.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code DAILY_QUEST}                 — list the active daily quest in each pool</li>
 *   <li>{@code DAILY_QUEST LIST}            — same as the bare command</li>
 *   <li>{@code DAILY_QUEST ACCEPT <pool>}   — accept the pool's active daily quest</li>
 *   <li>{@code DAILY_QUEST STATUS}          — show progress on the held daily quest</li>
 *   <li>{@code DAILY_QUEST COMPLETE}        — claim the daily bonus reward once complete</li>
 * </ul>
 */
public class DailyQuestCommand extends RegistrableCommand {

    public DailyQuestCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "daily_quest";
    }

    @Override
    public String shortDescription() {
        return "View and accept the rotating daily quests. Use DAILY_QUEST to see today's quests.";
    }

    @Override
    public String longDescription() {
        return "Usage: DAILY_QUEST <sub-command> [args]\n"
             + "  DAILY_QUEST                 — list today's active daily quest in each pool\n"
             + "  DAILY_QUEST LIST            — same as the bare command\n"
             + "  DAILY_QUEST ACCEPT <pool>   — accept a pool's active daily quest (e.g. DAILY_QUEST ACCEPT slayer)\n"
             + "  DAILY_QUEST STATUS          — show progress on your held daily quest\n"
             + "  DAILY_QUEST COMPLETE        — claim the daily bonus reward once complete";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"DAILY_QUEST".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.executeDailyQuest(args)));
    }
}
