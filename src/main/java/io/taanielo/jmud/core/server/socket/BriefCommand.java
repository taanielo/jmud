package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles BRIEF toggle commands ({@code BRIEF ON|OFF|TOGGLE|STATUS}).
 *
 * <p>When enabled, walking between rooms omits the room's prose description line and shows only the
 * room name, exits, items, and occupants. Explicit {@code LOOK} always renders the full description
 * regardless of this setting. Modeled on {@link AnsiCommand}.
 */
public class BriefCommand extends RegistrableCommand {
    public BriefCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "brief";
    }

    @Override
    public String shortDescription() {
        return "Toggle skipping full room descriptions when you move.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: BRIEF [on|off|toggle|status]
                 BRIEF          — show whether brief mode is currently ON or OFF.
                 BRIEF ON       — skip the room's prose description when you walk into a room.
                 BRIEF OFF      — show the full room description on every move (the default).
                 BRIEF TOGGLE   — flip the current setting.
               Explicit LOOK (and LOOK <target>/EXAMINE) always shows the full description, so you can\
               always read the full text on demand.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"BRIEF".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> context.updateBrief(args)));
    }
}
