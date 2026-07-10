package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code PARTY} command, letting players form and manage groups.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code PARTY}                — list current party members and their HP</li>
 *   <li>{@code PARTY FORM}           — create a new party with the issuing player as leader</li>
 *   <li>{@code PARTY INVITE <name>}  — send an invitation to another online player</li>
 *   <li>{@code PARTY ACCEPT}         — accept a pending invitation</li>
 *   <li>{@code PARTY DECLINE}        — decline a pending invitation</li>
 *   <li>{@code PARTY LEAVE}          — leave the current party (disbands if last member)</li>
 *   <li>{@code PARTY DISBAND}        — disband the party (leader only)</li>
 *   <li>{@code PARTY LOOT <mode>}    — set item loot mode to {@code free} or {@code round-robin} (leader only)</li>
 * </ul>
 */
public class PartyCommand extends RegistrableCommand {

    /**
     * Creates a {@code PartyCommand} and registers it with the given registry.
     *
     * @param registry the registry this command is part of
     */
    public PartyCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "party";
    }

    @Override
    public String shortDescription() {
        return "Form and manage a player party for shared XP. Use PARTY FORM to begin.";
    }

    @Override
    public String longDescription() {
        return "Usage: PARTY [sub-command] [args]\n"
             + "  PARTY                  — show current party members and their HP\n"
             + "  PARTY FORM             — create a new party (you become the leader)\n"
             + "  PARTY INVITE <player>  — invite an online player to your party\n"
             + "  PARTY ACCEPT           — accept a pending party invitation\n"
             + "  PARTY DECLINE          — decline a pending party invitation\n"
             + "  PARTY LEAVE            — leave your current party\n"
             + "  PARTY DISBAND          — disband the party (leader only)\n"
             + "  PARTY LOOT <mode>      — set loot mode: free (floor drops) or round-robin (leader only)\n"
             + "\n"
             + "XP earned from mob kills is split equally among party members in the same room.\n"
             + "In round-robin loot mode, dropped items are handed to party members in turn instead of\n"
             + "landing on the floor. PARTY LOOT with no argument reports the current mode.\n"
             + "Add {partyHp} to your prompt format to display party member HP at a glance.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"PARTY".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.executeParty(args)));
    }
}
