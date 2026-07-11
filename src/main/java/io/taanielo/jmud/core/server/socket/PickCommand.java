package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code PICK <container>} command, the rogue-only skill for unlocking a locked
 * container (e.g. a treasure chest) in the current room.
 *
 * <p>The attempt rolls an independent pick-success chance (scaling with rogue level) and a trap
 * chance; a sprung trap damages the rogue, while a successful pick unlocks the container. The game
 * logic lives in {@code GameActionService.pickLock} via {@link SocketCommandContext#pickLock(String)}.
 */
public class PickCommand extends RegistrableCommand {

    public PickCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "pick";
    }

    @Override
    public String shortDescription() {
        return "Attempt to unlock a locked container (rogue skill only).";
    }

    @Override
    public String longDescription() {
        return """
               Usage: PICK <container>
                 Attempts to pick the lock on a locked container in the room. Rogues only.
                 Success scales with your level; a hidden trap may spring and injure you.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"PICK".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        if (args.isBlank()) {
            return Optional.of(new SocketCommandMatch(
                this, context -> context.writeLineWithPrompt("Usage: PICK <container>")));
        }
        return Optional.of(new SocketCommandMatch(this, context -> context.pickLock(args)));
    }
}
