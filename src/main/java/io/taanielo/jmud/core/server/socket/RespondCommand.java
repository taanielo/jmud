package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code RESPOND <number>} command, which selects a numbered reply in the conversation
 * started by {@link TalkCommand TALK}.
 *
 * <p>The selection advances the active dialogue tree and displays the NPC's next line and options.
 * The game logic lives in {@link SocketCommandContext#respond(String)}.
 */
public class RespondCommand extends RegistrableCommand {

    public RespondCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "respond";
    }

    @Override
    public String shortDescription() {
        return "Choose a numbered reply in an NPC conversation.";
    }

    @Override
    public String longDescription() {
        return "Usage: RESPOND <number>\n"
             + "  Selects one of the numbered replies shown while talking to an NPC (see TALK).";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"RESPOND".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        if (args.isBlank()) {
            return Optional.of(new SocketCommandMatch(
                this, context -> context.writeLineWithPrompt("Usage: RESPOND <number>")));
        }
        return Optional.of(new SocketCommandMatch(this, context -> context.respond(args)));
    }
}
