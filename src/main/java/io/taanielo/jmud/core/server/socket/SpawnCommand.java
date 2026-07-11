package io.taanielo.jmud.core.server.socket;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.mob.MobId;
import io.taanielo.jmud.core.mob.MobInstance;
import io.taanielo.jmud.core.mob.MobRegistry;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;

/**
 * Handles the wizard-only {@code SPAWN} command, which instantiates a mob from a template id into a
 * room (e.g. {@code SPAWN goblin} or {@code SPAWN goblin cave-entrance}).
 *
 * <p>When no room id is supplied the mob is spawned into the admin's current room. The spawn is
 * delegated to {@link MobRegistry#spawnInstance}, which registers the instance so it joins AI and
 * combat from the next tick. A summon notice is fanned out to the target room through
 * {@link MessageBroadcaster} (AGENTS.md §3.3). All mutation runs on the tick thread via the player
 * command queue (AGENTS.md §5). Access is gated by {@link WizardPolicy}.
 */
public class SpawnCommand extends RegistrableCommand {

    private final WizardPolicy wizardPolicy;
    private final @Nullable MobRegistry mobRegistry;
    private final RoomService roomService;
    private final MessageBroadcaster messageBroadcaster;

    /**
     * Creates the SPAWN command and registers it with the given registry.
     *
     * @param registry           the command registry to register with
     * @param wizardPolicy       policy deciding which players may spawn mobs
     * @param mobRegistry        the live mob registry used to instantiate templates; may be
     *                           {@code null} when the mob subsystem failed to load
     * @param roomService        service used to resolve the admin's room and validate a target room
     * @param messageBroadcaster scoped delivery service used to notify the target room
     */
    public SpawnCommand(
        SocketCommandRegistry registry,
        WizardPolicy wizardPolicy,
        @Nullable MobRegistry mobRegistry,
        RoomService roomService,
        MessageBroadcaster messageBroadcaster
    ) {
        super(registry);
        this.wizardPolicy = Objects.requireNonNull(wizardPolicy, "Wizard policy is required");
        this.mobRegistry = mobRegistry;
        this.roomService = Objects.requireNonNull(roomService, "Room service is required");
        this.messageBroadcaster = Objects.requireNonNull(messageBroadcaster, "Message broadcaster is required");
    }

    @Override
    public String name() {
        return "spawn";
    }

    @Override
    public String shortDescription() {
        return "Spawn a mob by template id (wizard only).";
    }

    @Override
    public String longDescription() {
        return """
               Usage: SPAWN <mob-id> [room-id]
                 Instantiates a mob from the given template id into the named room, or your
                 current room when no room is given. Restricted to wizards.\
               """;
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"SPAWN".equals(parts[0])) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, context -> handleSpawn(context, parts[1])));
    }

    private void handleSpawn(SocketCommandContext context, String args) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to use SPAWN.");
            return;
        }
        Player player = context.getPlayer();
        if (!wizardPolicy.isWizard(player)) {
            context.writeLineWithPrompt("Denied. The SPAWN command is restricted to wizards.");
            return;
        }
        if (mobRegistry == null) {
            context.writeLineWithPrompt("The mob subsystem is not available.");
            return;
        }
        String[] tokens = args.trim().split("\\s+", 2);
        String mobArg = tokens[0];
        if (mobArg.isEmpty()) {
            context.writeLineWithPrompt("Usage: SPAWN <mob-id> [room-id]");
            return;
        }
        Username admin = player.getUsername();
        RoomId targetRoom;
        if (tokens.length > 1 && !tokens[1].isBlank()) {
            String roomArg = tokens[1].trim();
            RoomId requested = RoomId.of(roomArg);
            Optional<Room> room = roomService.findRoom(requested);
            if (room.isEmpty()) {
                context.writeLineWithPrompt("No room with id '" + roomArg + "' exists.");
                return;
            }
            targetRoom = requested;
        } else {
            targetRoom = roomService.ensurePlayerLocation(admin);
        }

        Optional<MobInstance> spawned = mobRegistry.spawnInstance(MobId.of(mobArg), targetRoom);
        if (spawned.isEmpty()) {
            context.writeLineWithPrompt("No mob template with id '" + mobArg + "' exists.");
            return;
        }
        String mobName = spawned.get().template().name();
        messageBroadcaster.broadcastToRoom(
            targetRoom,
            new PlainTextMessage("A " + mobName + " is summoned into existence."),
            Set.of(admin));
        context.writeLineWithPrompt(
            "You summon a " + mobName + " into " + targetRoom.getValue() + ".");
    }
}
