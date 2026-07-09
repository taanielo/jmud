package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code TALK <npc name>} command, which starts a conversation with a dialogue-capable
 * NPC in the player's current room.
 *
 * <p>The conversation displays the NPC's opening line and a numbered list of responses; the player
 * advances it with {@link RespondCommand RESPOND &lt;number&gt;}. The game logic lives in
 * {@link SocketCommandContext#talk(String)}.
 */
public class TalkCommand extends RegistrableCommand {

    public TalkCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "talk";
    }

    @Override
    public String shortDescription() {
        return "Talk to an NPC in the room to begin a dialogue.";
    }

    @Override
    public String longDescription() {
        return "Usage: TALK <npc name>\n"
             + "  Starts a conversation with a dialogue-capable NPC in your current room.\n"
             + "  Choose numbered replies with RESPOND <number>.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"TALK".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        if (args.isBlank()) {
            return Optional.of(new SocketCommandMatch(
                this, context -> context.writeLineWithPrompt("Usage: TALK <npc name>")));
        }
        return Optional.of(new SocketCommandMatch(this, context -> context.talk(args)));
    }
}
