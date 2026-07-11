package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code STEAL <npc>} command, the rogue-only skill for pickpocketing gold from a
 * target NPC in the current room.
 *
 * <p>The attempt rolls a success chance that scales with rogue level: on success gold is lifted
 * from the NPC, while on failure the rogue is caught and the NPC turns hostile. The game logic
 * lives in {@code GameActionService.steal} via {@link SocketCommandContext#steal(String)}.
 */
public class StealCommand extends RegistrableCommand {

    public StealCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "steal";
    }

    @Override
    public String shortDescription() {
        return "Attempt to pickpocket gold from an NPC (rogue skill only).";
    }

    @Override
    public String longDescription() {
        return """
               Usage: STEAL <npc>
                 Attempts to pickpocket gold from an NPC in the room. Rogues only.
                 Success scales with your level; if you are caught, the NPC turns on you.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"STEAL".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        if (args.isBlank()) {
            return Optional.of(new SocketCommandMatch(
                this, context -> context.writeLineWithPrompt("Usage: STEAL <npc>")));
        }
        return Optional.of(new SocketCommandMatch(this, context -> context.steal(args)));
    }
}
