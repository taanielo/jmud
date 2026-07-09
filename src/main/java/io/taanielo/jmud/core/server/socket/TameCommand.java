package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code TAME <mob>} command, which permanently captures a charmable mob in the current
 * room as a persistent companion.
 *
 * <p>A tamed companion follows its owner between rooms, fights hostile mobs at their side, and is
 * saved to the player's file so it survives logout/login. Only mobs flagged {@code charmable} may be
 * tamed, and a player may keep a limited number of companions at once. The game logic lives in
 * {@code MobRegistry.processTame} via {@link SocketCommandContext#tame(String)}.
 */
public class TameCommand extends RegistrableCommand {

    public TameCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "tame";
    }

    @Override
    public String shortDescription() {
        return "Permanently tame a charmable mob as a companion that follows and fights for you.";
    }

    @Override
    public String longDescription() {
        return "Usage: TAME <mob>\n"
             + "  Permanently captures a charmable mob in your room as a loyal companion. The\n"
             + "  companion follows you between rooms, fights hostile mobs at your side, and is saved\n"
             + "  so it survives logout. Only some creatures can be tamed, and you may keep only a few\n"
             + "  companions at once. Use COMPANIONS to list your tamed pets.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"TAME".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        if (args.isBlank()) {
            return Optional.of(new SocketCommandMatch(
                this, context -> context.writeLineWithPrompt("Usage: TAME <mob>")));
        }
        return Optional.of(new SocketCommandMatch(this, context -> context.tame(args)));
    }
}
