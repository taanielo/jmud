package io.taanielo.jmud.core.server.socket;

import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;

/**
 * Handles the {@code GIVE} command, which hands an item from the player's inventory to another
 * player currently in the same room.
 *
 * <p>Usage: {@code GIVE <playername> <itemname>}
 *
 * <p>The target player must be online and in the sender's current room (the same same-room check
 * pattern used by {@link WhisperCommand}), otherwise the sender sees {@code <name> is not here.}
 * The actual item transfer (removing the item from the sender's inventory, unequipping it first
 * if worn, and adding it to the recipient's inventory) is delegated to
 * {@code GameActionService.giveItem} via {@link SocketCommandContext#giveItem}.
 */
public class GiveCommand extends RegistrableCommand {

    private final RoomService roomService;

    /**
     * Creates a {@code GiveCommand} and registers it with the given registry.
     *
     * @param registry    the registry to register this command with
     * @param roomService service used to verify the sender and target share a room
     */
    public GiveCommand(SocketCommandRegistry registry, RoomService roomService) {
        super(registry);
        this.roomService = Objects.requireNonNull(roomService, "Room service is required");
    }

    @Override
    public String name() {
        return "give";
    }

    @Override
    public String shortDescription() {
        return "Give an item from your inventory to a player in your room.";
    }

    @Override
    public String longDescription() {
        return "Usage: GIVE <playername> <itemname>\n"
             + "  Hands the named item from your inventory to the named player, who\n"
             + "  must be in your current room.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"GIVE".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> handleGive(context, args)));
    }

    private void handleGive(SocketCommandContext context, String args) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to give items.");
            return;
        }
        if (args.isBlank()) {
            context.writeLineWithPrompt("Usage: GIVE <playername> <itemname>");
            return;
        }
        String[] argParts = args.split("\\s+", 2);
        String targetName = argParts[0];
        if (argParts.length < 2 || argParts[1].isBlank()) {
            context.writeLineWithPrompt("Usage: GIVE <playername> <itemname>");
            return;
        }
        String itemInput = argParts[1].trim();

        Player sender = context.getPlayer();

        // Find the target among online players (case-insensitive).
        Username targetUsername = context.onlinePlayerNames().stream()
                .filter(u -> u.equals(Username.of(targetName)))
                .findFirst()
                .orElse(null);

        if (targetUsername == null) {
            context.writeLineWithPrompt(targetName + " is not here.");
            return;
        }

        Optional<RoomId> senderRoom = roomService.findPlayerLocation(sender.getUsername());
        Optional<RoomId> targetRoom = roomService.findPlayerLocation(targetUsername);
        if (senderRoom.isEmpty() || targetRoom.isEmpty() || !senderRoom.get().equals(targetRoom.get())) {
            context.writeLineWithPrompt(targetName + " is not here.");
            return;
        }

        context.giveItem(targetUsername, itemInput);
    }
}
