package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code CORPSE} command, telling a fallen player where their remains lie and how to get
 * back to them before the corpse decays.
 *
 * <p>{@code CORPSE} takes no arguments. Parsing lives here; the corpse lookup, decay countdown, and
 * ferry-aware routing live behind {@link SocketCommandContext#corpse()} (in {@code
 * CorpseLocatorService}, reusing {@code WAYFIND}'s routing).
 */
public class CorpseCommand extends RegistrableCommand {

    public CorpseCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "corpse";
    }

    @Override
    public String shortDescription() {
        return "Locate your corpse and get turn-by-turn directions back to it before it decays.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: CORPSE
                 When you die above the newbie grace levels, your gold and unequipped items drop into
                 a lootable corpse where you fell, and that corpse decays after a few minutes. CORPSE
                 tells you where those remains are and how to reach them in time.
                 With no corpse in the world (you have not died, or it was looted, decayed, or consumed
                 by a resurrection) it simply says so. If your corpse is in the room with you, it
                 confirms you are standing on it — loot it. Otherwise it names the room your corpse is
                 in, how much gold it holds, roughly how long before it decays, and the shortest
                 walking route there as turn-by-turn compass directions, using a ferry leg when that is
                 the shortest or only way across (exactly like WAYFIND). If no route back is known it
                 still names the room but tells you it cannot chart a path. CORPSE never moves you and
                 never changes anything — it only reports.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"CORPSE".equals(parts[0])) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, SocketCommandContext::corpse));
    }
}
