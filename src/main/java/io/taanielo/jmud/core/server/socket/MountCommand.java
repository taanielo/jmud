package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code MOUNT <name>} command, saddling the player up on a rideable mount they own.
 *
 * <p>While mounted outdoors each step costs fewer move points, letting the rider cover more ground
 * before needing to {@code REST}. Mounts may only be summoned outdoors, and the ridden state is
 * broken automatically on entering combat or moving indoors. The game logic lives in
 * {@code GameActionService.mount} via {@link SocketCommandContext#mount(String)}.
 */
public class MountCommand extends RegistrableCommand {

    public MountCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "mount";
    }

    @Override
    public String shortDescription() {
        return "Ride a mount you own to travel faster.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: MOUNT <name>
                 Climbs onto a rideable mount (e.g. a pony or a warhorse) carried in your inventory.
                 While mounted outdoors, each step costs fewer move points, so you can travel
                 farther before you must REST. You cannot mount indoors or underground, and you are
                 thrown from the saddle the moment you enter combat or ride into an enclosed space.
                 Use DISMOUNT to climb down. Your SCORE shows the mount you are riding.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"MOUNT".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        if (args.isEmpty()) {
            return Optional.of(new SocketCommandMatch(
                this, context -> context.writeLineWithPrompt("Usage: MOUNT <name>")));
        }
        return Optional.of(new SocketCommandMatch(this, context -> context.mount(args)));
    }
}
