package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code TAUNT} command: the Warrior-only skill that forces a mob already in combat in
 * the player's current room to prioritise attacking the taunter for the next few AI decisions,
 * peeling it off an ally. Parsing of the target name and all validation happen in
 * {@link SocketCommandContext#executeTaunt(String)}.
 */
public class TauntCommand extends RegistrableCommand {
    public TauntCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "taunt";
    }

    @Override
    public String shortDescription() {
        return "Force a mob to attack you instead of your allies.";
    }

    @Override
    public String longDescription() {
        return """
                Usage: TAUNT <mob>
                  Bellow a challenge at the named mob in your current room, forcing it to attack you
                  instead of your party members for the next few rounds. The mob must already be in
                  combat. A Warrior-only skill: it costs move points, has a short cooldown, and cannot
                  hold aggro permanently — use it to peel a mob off a squishy ally, not to tank
                  forever.""";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        String token = parts[0];
        if (!"TAUNT".equals(token)) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.executeTaunt(args)));
    }
}
