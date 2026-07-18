package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code GUILD QUEST} family: shows the caller's guild its current cooperative guild
 * quest and progress toward it.
 *
 * <p>Every guild is assigned one active guild quest at a time — "slay N of a mob type" — rotated daily
 * from a shared pool. Progress is <em>cooperative</em>: any online member's kill of the matching mob
 * type, anywhere in the world, advances the guild's shared counter automatically (no ACCEPT needed).
 * On completion the reward gold is paid into the guild treasury and a {@code [Guild]} announcement
 * fires, and a fresh objective is rolled.
 *
 * <p>{@code GUILD QUEST} is exposed both as its own command and (for convenience) as a sub-command of
 * {@code GUILD}; this class registers the standalone {@code GUILD_QUEST}/{@code GQUEST} tokens.
 */
public class GuildQuestCommand extends RegistrableCommand {

    /**
     * Creates a {@code GuildQuestCommand} and registers it with the given registry.
     *
     * @param registry the registry this command is part of
     */
    public GuildQuestCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "guild_quest";
    }

    @Override
    public String shortDescription() {
        return "Show your guild's cooperative guild quest and shared progress. Alias: GQUEST.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: GUILD_QUEST (alias: GQUEST, or GUILD QUEST)

               Every guild is assigned one active guild quest at a time — slay N of a mob type,
               rotated daily from a shared pool scaled to your guild's level. Progress is cooperative:
               any online member's kill of the matching mob type, anywhere in the world, advances the
               guild's shared counter automatically. You do NOT accept it — progress accrues for the
               whole guild the moment the quest is assigned.

               On completion the reward gold is paid straight into the guild treasury (counting toward
               your guild's lifetime deposited gold and next level), a [Guild] announcement fires, and a
               new objective is rolled. Every member can view progress with GUILD_QUEST.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if ("GUILD_QUEST".equals(token) || "GQUEST".equals(token)) {
            return Optional.of(new SocketCommandMatch(this, context -> context.executeGuildQuest(parts[1])));
        }
        return Optional.empty();
    }
}
