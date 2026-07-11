package io.taanielo.jmud.core.server.socket;

import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.TellService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;

/**
 * Handles the {@code WHISPER} command, which is like {@link TellCommand} but restricted
 * to a single named target who must currently be in the same room as the sender.
 *
 * <p>Usage: {@code WHISPER <playername> <message>}
 *
 * <p>The sender sees: {@code You whisper to <Name>: <message>}
 * <br>The recipient sees: {@code <Name> whispers to you: <message>}
 *
 * <p>Errors are reported to the sender when the target player is not online, or is
 * online but not currently in the same room, or when the command is invoked without
 * sufficient arguments.
 */
public class WhisperCommand extends RegistrableCommand {

    private final RoomService roomService;
    private final TellService tellService;

    /**
     * Creates a {@code WhisperCommand} and registers it with the given registry.
     *
     * @param registry    the registry to register this command with
     * @param roomService service used to verify the sender and target share a room
     * @param tellService service that records the last private-message sender for {@code REPLY}
     */
    public WhisperCommand(SocketCommandRegistry registry, RoomService roomService, TellService tellService) {
        super(registry);
        this.roomService = Objects.requireNonNull(roomService, "Room service is required");
        this.tellService = Objects.requireNonNull(tellService, "Tell service is required");
    }

    @Override
    public String name() {
        return "whisper";
    }

    @Override
    public String shortDescription() {
        return "Send a private message to a player in your room.";
    }

    @Override
    public String longDescription() {
        return """
               Usage: WHISPER <playername> <message>
                 Sends a private message to the named player, who must be in your
                 current room.
                 The recipient sees: <YourName> whispers to you: <message>\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"WHISPER".equals(parts[0])) {
            return Optional.empty();
        }
        String args = parts[1];
        return Optional.of(new SocketCommandMatch(this, context -> handleWhisper(context, args)));
    }

    private void handleWhisper(SocketCommandContext context, String args) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to whisper.");
            return;
        }
        if (args.isBlank()) {
            context.writeLineWithPrompt("Usage: WHISPER <playername> <message>");
            return;
        }
        String[] argParts = args.split("\\s+", 2);
        String targetName = argParts[0];
        if (argParts.length < 2 || argParts[1].isBlank()) {
            context.writeLineWithPrompt("Usage: WHISPER <playername> <message>");
            return;
        }
        String message = argParts[1].trim();

        Player sender = context.getPlayer();
        String senderName = sender.getUsername().getValue();

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

        context.sendToUsername(targetUsername, senderName + " whispers to you: " + message);
        // The recipient just received a private message; make the sender their REPLY target.
        tellService.recordReceivedTell(targetUsername, sender.getUsername());
        context.writeLineSafe("You whisper to " + targetUsername.getValue() + ": " + message);
        context.sendPrompt();
    }
}
