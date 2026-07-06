package io.taanielo.jmud.core.server.socket;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;

/**
 * Handles the {@code SHOUT} command, which is like {@link SayCommand} but is also
 * audible in every room directly adjacent to the speaker's current room (i.e. reachable
 * via a single exit), in addition to the speaker's own room.
 *
 * <p>Usage: {@code SHOUT <message>}
 *
 * <p>Occupants of the speaker's own room see: {@code <Name> shouts "<message>"}
 * <br>Occupants of adjacent rooms see: {@code You hear <Name> shout "<message>" from nearby.}
 *
 * <p>Delivery is fanned out via {@link MessageBroadcaster#broadcastToRoom}, the single
 * sanctioned multi-recipient delivery path (AGENTS.md §3.3), once per room (the speaker's
 * own room plus each adjacent room resolved from {@code Room.getExits()}).
 */
public class ShoutCommand extends RegistrableCommand {

    private final RoomService roomService;
    private final MessageBroadcaster messageBroadcaster;

    /**
     * Creates a {@code ShoutCommand} and registers it with the given registry.
     *
     * @param registry           the registry to register this command with
     * @param roomService        service used to resolve the speaker's room and its exits
     * @param messageBroadcaster scoped delivery service used to fan out to each room
     */
    public ShoutCommand(SocketCommandRegistry registry, RoomService roomService, MessageBroadcaster messageBroadcaster) {
        super(registry);
        this.roomService = Objects.requireNonNull(roomService, "Room service is required");
        this.messageBroadcaster = Objects.requireNonNull(messageBroadcaster, "Message broadcaster is required");
    }

    @Override
    public String name() {
        return "shout";
    }

    @Override
    public String shortDescription() {
        return "Speak to your room and every adjacent room.";
    }

    @Override
    public String longDescription() {
        return "Usage: SHOUT <message>\n"
             + "  Sends a message to everyone in your current room, and to everyone in\n"
             + "  every room reachable via one exit from your room.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"SHOUT".equals(parts[0])) {
            return Optional.empty();
        }
        String message = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> handleShout(context, message)));
    }

    private void handleShout(SocketCommandContext context, String message) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to speak.");
            return;
        }
        if (message.isEmpty()) {
            context.writeLineWithPrompt("Shout what?");
            return;
        }
        Player player = context.getPlayer();
        Username speaker = player.getUsername();
        String senderName = speaker.getValue();

        Optional<RoomId> currentRoomId = roomService.findPlayerLocation(speaker);
        if (currentRoomId.isPresent()) {
            RoomId roomId = currentRoomId.get();
            Set<Username> exclude = Set.of(speaker);
            messageBroadcaster.broadcastToRoom(
                roomId, new PlainTextMessage(senderName + " shouts \"" + message + "\""), exclude);

            Set<RoomId> adjacentRoomIds = new LinkedHashSet<>(roomService.getExits(roomId).values());
            adjacentRoomIds.remove(roomId);
            for (RoomId adjacentRoomId : adjacentRoomIds) {
                messageBroadcaster.broadcastToRoom(
                    adjacentRoomId,
                    new PlainTextMessage("You hear " + senderName + " shout \"" + message + "\" from nearby."),
                    exclude);
            }
        }

        context.writeLineSafe("You shout \"" + message + "\"");
        context.sendPrompt();
    }
}
