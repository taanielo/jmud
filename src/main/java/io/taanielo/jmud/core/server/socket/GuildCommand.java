package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code GUILD} command family, letting players found and manage a persistent guild and
 * chat privately with guildmates.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code GUILD}                — show your guild's roster (or usage when guildless)</li>
 *   <li>{@code GUILD CREATE <name>}  — found a new guild (costs gold; you become the leader)</li>
 *   <li>{@code GUILD INVITE <player>}— invite an online, guildless player (leader or officer)</li>
 *   <li>{@code GUILD ACCEPT}         — accept a pending guild invitation</li>
 *   <li>{@code GUILD DECLINE}        — decline a pending guild invitation</li>
 *   <li>{@code GUILD LEAVE}          — leave your guild (leadership transfers if you were leader)</li>
 *   <li>{@code GUILD KICK <player>}  — remove a member (leader or officer)</li>
 *   <li>{@code GUILD PROMOTE <player>}— promote a member to officer (leader only)</li>
 *   <li>{@code GUILD DEMOTE <player>}— demote an officer back to member (leader only)</li>
 *   <li>{@code GUILD DISBAND}        — permanently disband your guild (leader only, needs confirm)</li>
 *   <li>{@code GUILD WHO}            — list members with online/offline status</li>
 *   <li>{@code GUILD BANK}           — show the guild's shared treasury balance</li>
 *   <li>{@code GUILD DEPOSIT <amt>}  — deposit gold from your balance into the treasury</li>
 *   <li>{@code GUILD WITHDRAW <amt>} — withdraw gold from the treasury (leader only)</li>
 *   <li>{@code GUILD VAULT}          — list the items in the guild's shared item vault</li>
 *   <li>{@code GUILD STORE <item>}   — deposit an item into the guild vault (any member)</li>
 *   <li>{@code GUILD CLAIM <item>}   — take an item from the guild vault (leader or officer)</li>
 *   <li>{@code GUILD QUEST}          — show the guild's cooperative guild quest and shared progress</li>
 *   <li>{@code GUILD <message>}      — send a message to online guildmates (also {@code GC})</li>
 * </ul>
 */
public class GuildCommand extends RegistrableCommand {

    /**
     * Creates a {@code GuildCommand} and registers it with the given registry.
     *
     * @param registry the registry this command is part of
     */
    public GuildCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "guild";
    }

    @Override
    public String shortDescription() {
        return "Found and manage a persistent guild. Use GUILD CREATE <name> to begin. Alias: GC (chat).";
    }

    @Override
    public String longDescription() {
        return "Usage: GUILD [sub-command] [args]\n"
             + "  GUILD                  — show your guild's roster\n"
             + "  GUILD CREATE <name>    — found a new guild (costs "
                 + io.taanielo.jmud.core.guild.GuildService.CREATION_COST_GOLD + " gold)\n"
             + "  GUILD INVITE <player>  — invite an online player (leader or officer)\n"
             + "  GUILD ACCEPT           — accept a pending guild invitation\n"
             + "  GUILD DECLINE          — decline a pending guild invitation\n"
             + "  GUILD LEAVE            — leave your guild\n"
             + "  GUILD KICK <player>    — remove a member (leader or officer)\n"
             + "  GUILD PROMOTE <player> — promote a member to officer (leader only)\n"
             + "  GUILD DEMOTE <player>  — demote an officer to member (leader only)\n"
             + "  GUILD DISBAND          — disband your guild (leader only; GUILD DISBAND CONFIRM)\n"
             + "  GUILD WHO              — list members with online/offline status\n"
             + "  GUILD BANK             — show the guild treasury balance\n"
             + "  GUILD DEPOSIT <amount> — deposit gold into the guild treasury\n"
             + "  GUILD WITHDRAW <amount>— withdraw gold from the treasury (leader only)\n"
             + "  GUILD VAULT            — list items in the guild's shared item vault\n"
             + "  GUILD STORE <item>     — deposit an item into the guild vault (any member)\n"
             + "  GUILD CLAIM <item>     — take an item from the guild vault (leader or officer)\n"
             + "  GUILD QUEST            — show the guild's cooperative guild quest (also GUILD_QUEST/GQUEST)\n"
             + "  GUILD <message>        — chat to online guildmates (or use GC <message>)\n"
             + "\n"
             + "Guild quests: every guild is assigned one shared 'slay N of a mob type' objective at a\n"
             + "time, rotated daily and scaled to the guild's level. Any online member's kill of the\n"
             + "matching mob — anywhere in the world — credits the whole guild automatically (no ACCEPT).\n"
             + "On completion the reward gold is paid into the guild treasury (advancing the guild's\n"
             + "lifetime total and level) and a [Guild] announcement fires; a new objective is then rolled.\n"
             + "\n"
             + "Guild levels (1-5): every gold piece deposited via GUILD DEPOSIT adds to the guild's\n"
             + "lifetime deposited total (withdrawals never reduce it). Crossing a threshold raises the\n"
             + "guild's level, and each level above 1 grows the shared item vault by 10 slots:\n"
             + "  level 1 (0 gold) — 40 slots      level 4 (5,000 gold) — 70 slots\n"
             + "  level 2 (500 gold) — 50 slots    level 5 (15,000 gold) — 80 slots\n"
             + "  level 3 (2,000 gold) — 60 slots\n"
             + "GUILD and GUILD VAULT show your level, lifetime total, and progress to the next level.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        String args = parts[1];
        if ("GC".equals(token)) {
            return Optional.of(new SocketCommandMatch(this, context -> context.guildChat(args)));
        }
        if ("GUILD".equals(token)) {
            return Optional.of(new SocketCommandMatch(this, context -> context.executeGuild(args)));
        }
        return Optional.empty();
    }
}
