package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

/**
 * Handles the {@code GATHER} command, the non-combat activity for harvesting a raw crafting material
 * from a resource node (an ore vein, a herb patch) in the current room.
 *
 * <p>Harvesting depletes the node until it respawns after a fixed number of ticks. The game logic
 * lives in {@code ResourceGatheringService} via {@link SocketCommandContext#gather()}; a node that is
 * absent or already depleted yields a clear failure message.
 */
public class GatherCommand extends RegistrableCommand {

    public GatherCommand(SocketCommandRegistry registry) {
        super(registry);
    }

    @Override
    public String name() {
        return "gather";
    }

    @Override
    public String shortDescription() {
        return "Harvest raw crafting materials from a resource node in the room.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: GATHER
                 Harvests the resource node (an ore vein, a herb patch, and the like) in your current
                 room, adding the raw material to your inventory. A harvested node is stripped bare and
                 must respawn before it can be worked again. Take the gathered materials to a blacksmith
                 to CRAFT new gear.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"GATHER".equals(parts[0])) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, SocketCommandContext::gather));
    }
}
