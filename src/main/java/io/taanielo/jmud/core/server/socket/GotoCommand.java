package io.taanielo.jmud.core.server.socket;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.PlayerLocationService;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;

/**
 * Handles the wizard-only {@code GOTO} command, which teleports the acting administrator to any
 * room by its id (e.g. {@code GOTO training-yard}).
 *
 * <p>The command validates the target room exists, then relocates the admin via
 * {@link PlayerLocationService#movePlayerTo}. Departure and arrival notices are fanned out to the
 * old and new rooms through {@link MessageBroadcaster} (the only sanctioned multi-recipient path,
 * AGENTS.md §3.3), excluding the admin. It runs on the tick thread via the player command queue, so
 * the location mutation is single-writer safe (AGENTS.md §5). Access is gated by {@link WizardPolicy}.
 */
public class GotoCommand extends RegistrableCommand {

    private final WizardPolicy wizardPolicy;
    private final PlayerLocationService playerLocationService;
    private final RoomService roomService;
    private final MessageBroadcaster messageBroadcaster;

    /**
     * Creates the GOTO command and registers it with the given registry.
     *
     * @param registry              the command registry to register with
     * @param wizardPolicy          policy deciding which players may teleport
     * @param playerLocationService service used to relocate the admin's tracked location
     * @param roomService           service used to resolve and validate the target room
     * @param messageBroadcaster    scoped delivery service used to notify the old and new rooms
     */
    public GotoCommand(
        SocketCommandRegistry registry,
        WizardPolicy wizardPolicy,
        PlayerLocationService playerLocationService,
        RoomService roomService,
        MessageBroadcaster messageBroadcaster
    ) {
        super(registry);
        this.wizardPolicy = Objects.requireNonNull(wizardPolicy, "Wizard policy is required");
        this.playerLocationService = Objects.requireNonNull(playerLocationService, "Player location service is required");
        this.roomService = Objects.requireNonNull(roomService, "Room service is required");
        this.messageBroadcaster = Objects.requireNonNull(messageBroadcaster, "Message broadcaster is required");
    }

    @Override
    public String name() {
        return "goto";
    }

    @Override
    public String shortDescription() {
        return "Teleport to a room by id (wizard only).";
    }

    @Override
    public String longDescription() {
        return "Usage: GOTO <room-id>\n"
             + "  Instantly teleports you to the room with the given id. Restricted to wizards.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"GOTO".equals(parts[0])) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, context -> handleGoto(context, parts[1])));
    }

    private void handleGoto(SocketCommandContext context, String args) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to use GOTO.");
            return;
        }
        Player player = context.getPlayer();
        if (!wizardPolicy.isWizard(player)) {
            context.writeLineWithPrompt("Denied. The GOTO command is restricted to wizards.");
            return;
        }
        String target = args.trim();
        if (target.isEmpty()) {
            context.writeLineWithPrompt("Usage: GOTO <room-id>");
            return;
        }
        RoomId destinationId = RoomId.of(target);
        Optional<Room> destination = roomService.findRoom(destinationId);
        if (destination.isEmpty()) {
            context.writeLineWithPrompt("No room with id '" + target + "' exists.");
            return;
        }
        Username admin = player.getUsername();
        Set<Username> excludeAdmin = Set.of(admin);
        roomService.findPlayerLocation(admin).ifPresent(fromRoomId ->
            messageBroadcaster.broadcastToRoom(
                fromRoomId,
                new PlainTextMessage(admin.getValue() + " vanishes in a puff of smoke."),
                excludeAdmin));

        playerLocationService.movePlayerTo(admin, destinationId);

        messageBroadcaster.broadcastToRoom(
            destinationId,
            new PlainTextMessage(admin.getValue() + " arrives in a shimmer of arcane light."),
            excludeAdmin);

        context.writeLineSafe("You teleport to " + destination.get().getName() + ".");
        context.sendLook();
    }
}
