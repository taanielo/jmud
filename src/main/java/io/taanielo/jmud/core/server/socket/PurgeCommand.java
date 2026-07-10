package io.taanielo.jmud.core.server.socket;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.MessageBroadcaster;
import io.taanielo.jmud.core.messaging.PlainTextMessage;
import io.taanielo.jmud.core.mob.MobRegistry;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;

/**
 * Handles the wizard-only {@code PURGE} command, which removes an entity from the world by name.
 *
 * <p>Resolution order: a live mob matching the argument in the admin's current room is removed
 * first (via {@link MobRegistry#purgeMob}); otherwise the argument is treated as a player name and,
 * provided that player is not currently online, their persisted record is deleted via
 * {@link PlayerRepository#deletePlayer}. Mob removal notices are fanned out to the room through
 * {@link MessageBroadcaster} (AGENTS.md §3.3). It runs on the tick thread via the player command
 * queue (AGENTS.md §5). Access is gated by {@link WizardPolicy}.
 */
public class PurgeCommand extends RegistrableCommand {

    private final WizardPolicy wizardPolicy;
    private final @Nullable MobRegistry mobRegistry;
    private final RoomService roomService;
    private final PlayerRepository playerRepository;
    private final MessageBroadcaster messageBroadcaster;

    /**
     * Creates the PURGE command and registers it with the given registry.
     *
     * @param registry           the command registry to register with
     * @param wizardPolicy       policy deciding which players may purge entities
     * @param mobRegistry        the live mob registry used to remove mob instances; may be
     *                           {@code null} when the mob subsystem failed to load
     * @param roomService        service used to resolve the admin's current room
     * @param playerRepository   repository used to delete an offline player's persisted record
     * @param messageBroadcaster scoped delivery service used to notify the room of a purge
     */
    public PurgeCommand(
        SocketCommandRegistry registry,
        WizardPolicy wizardPolicy,
        @Nullable MobRegistry mobRegistry,
        RoomService roomService,
        PlayerRepository playerRepository,
        MessageBroadcaster messageBroadcaster
    ) {
        super(registry);
        this.wizardPolicy = Objects.requireNonNull(wizardPolicy, "Wizard policy is required");
        this.mobRegistry = mobRegistry;
        this.roomService = Objects.requireNonNull(roomService, "Room service is required");
        this.playerRepository = Objects.requireNonNull(playerRepository, "Player repository is required");
        this.messageBroadcaster = Objects.requireNonNull(messageBroadcaster, "Message broadcaster is required");
    }

    @Override
    public String name() {
        return "purge";
    }

    @Override
    public String shortDescription() {
        return "Remove a mob or offline player (wizard only).";
    }

    @Override
    public String longDescription() {
        return "Usage: PURGE <mob-name|player-name>\n"
             + "  Removes a matching mob from your current room, or deletes the persisted record of\n"
             + "  a named offline player. Restricted to wizards.";
    }

    @Override
    public Optional<SocketCommandMatch> match(String input) {
        String[] parts = SocketCommandParsing.splitInput(input);
        if (!"PURGE".equals(parts[0])) {
            return Optional.empty();
        }
        return Optional.of(new SocketCommandMatch(this, context -> handlePurge(context, parts[1])));
    }

    private void handlePurge(SocketCommandContext context, String args) {
        if (!context.isAuthenticated() || context.getPlayer() == null) {
            context.writeLineWithPrompt("You must be logged in to use PURGE.");
            return;
        }
        Player player = context.getPlayer();
        if (!wizardPolicy.isWizard(player)) {
            context.writeLineWithPrompt("Denied. The PURGE command is restricted to wizards.");
            return;
        }
        String target = args.trim();
        if (target.isEmpty()) {
            context.writeLineWithPrompt("Usage: PURGE <mob-name|player-name>");
            return;
        }
        Username admin = player.getUsername();
        RoomId roomId = roomService.ensurePlayerLocation(admin);

        if (mobRegistry != null) {
            Optional<String> purgedMob = mobRegistry.purgeMob(roomId, target);
            if (purgedMob.isPresent()) {
                String mobName = purgedMob.get();
                messageBroadcaster.broadcastToRoom(
                    roomId,
                    new PlainTextMessage("The " + mobName + " is purged from existence."),
                    Set.of(admin));
                context.writeLineWithPrompt("You purge the " + mobName + ".");
                return;
            }
        }

        Username targetUser = Username.of(target);
        if (context.onlinePlayerNames().contains(targetUser)) {
            context.writeLineWithPrompt("You cannot purge an online player. They must log out first.");
            return;
        }
        if (playerRepository.deletePlayer(targetUser)) {
            context.writeLineWithPrompt("Purged offline player " + targetUser.getValue() + ".");
            return;
        }
        context.writeLineWithPrompt("No mob or offline player named '" + target + "' found here.");
    }
}
