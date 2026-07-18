package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles AUTOASSIST toggle commands ({@code AUTOASSIST ON|OFF|TOGGLE|STATUS}).
 *
 * <p>When enabled, this player is automatically joined into a party-mate's fight the moment that
 * party-mate lands their opening attack on a fresh mob (one that was not already fighting anyone),
 * exactly as if they had typed {@code ASSIST <attacker>}. Auto-assist never fires when this player
 * is already in combat, resting, dead, absent from the room, or not partied with the attacker.
 * Manual {@code ASSIST} still works regardless of this toggle. Modeled on {@link AutoLootCommand}.
 */
public class AutoAssistCommand extends RegistrableCommand {
    public AutoAssistCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "autoassist";
    }

    @Override
    public String shortDescription() {
        return "Toggle automatically joining a party-mate's fight.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: AUTOASSIST [on|off|toggle|status]
                 AUTOASSIST        — show whether autoassist is currently ON or OFF.
                 AUTOASSIST ON     — auto-join a party-mate's fight when they open on a fresh mob.
                 AUTOASSIST OFF    — never auto-join; use manual ASSIST instead (the default).
                 AUTOASSIST TOGGLE — flip the current setting.
               Auto-assist only fires when you are in the same room, partied with the attacker, and \
               not already fighting, resting, or dead. Manual ASSIST works regardless of this toggle.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"AUTOASSIST".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.updateAutoAssist(args)));
    }
}
