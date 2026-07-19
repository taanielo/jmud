package io.taanielo.jmud.core.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.player.DeathSettings;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.RoomService;

/**
 * The single, canonical resolver for a player's death.
 *
 * <p>Every death path — a player felled while attacking a mob, PvP, an environmental hazard, or a
 * mob's own AI-driven attack ({@link io.taanielo.jmud.core.mob.MobRegistry}) — funnels through
 * {@link #resolveDeathIfNeeded} so the corpse-drop, newbie-grace, respawn-room, and player-facing
 * messaging are identical regardless of what dealt the killing blow (issue #805). Keeping this logic
 * in one place prevents the death behaviour from drifting between call sites (AGENTS.md §3.3).
 *
 * <p>All mutation performed here (corpse spawn, location clear) runs on the tick thread via its
 * callers (AGENTS.md §5); this service performs no blocking I/O.
 */
public final class PlayerDeathService {

    private final RoomService roomService;

    /**
     * Creates a death resolver backed by the shared world/room service.
     *
     * @param roomService the room service used to locate the dying player, spawn their corpse, and
     *                    resolve respawn-room display names
     */
    public PlayerDeathService(RoomService roomService) {
        this.roomService = Objects.requireNonNull(roomService, "Room service is required");
    }

    /**
     * Checks whether a player should die and resolves death if so.
     *
     * <p>If the target's HP is zero or below and not already dead, this method kills the target,
     * spawns a corpse (unless newbie grace applies), clears their location, and produces the
     * player-facing death messages plus a room-broadcast line.
     *
     * @param target   the player to check
     * @param attacker the killing player, or {@code null} for environmental/mob-initiated deaths
     * @return a result carrying the (possibly dead) target and the death messages; when the target is
     *     still alive the target is returned unchanged with no messages
     */
    public GameActionResult resolveDeathIfNeeded(Player target, @Nullable Player attacker) {
        Objects.requireNonNull(target, "Target is required");
        if (target.getVitals().hp() > 0) {
            return new GameActionResult(null, target, List.of());
        }
        if (target.isDead() && roomService.findPlayerLocation(target.getUsername()).isEmpty()) {
            return new GameActionResult(null, target, List.of());
        }
        RoomService.LookResult look = roomService.look(target.getUsername());
        Room room = look.room();

        // Newbie death grace: low-level characters keep all gold and items instead of dropping a
        // corpse, sparing them the corpse-run death spiral (see issue #520). At or above the grace
        // level the classic corpse-drop behaviour applies.
        boolean graceProtected = DeathSettings.isGraceProtected(target.getLevel());
        boolean corpseSpawned = false;
        Player deadTarget;
        if (graceProtected) {
            deadTarget = target.die();
        } else {
            int droppedGold = target.getGold();
            deadTarget = target.withGold(0).die();
            if (room != null) {
                roomService.spawnCorpse(deadTarget.getUsername(), room.getId(), droppedGold);
                corpseSpawned = true;
            }
        }

        List<GameMessage> messages = buildDeathMessages(attacker, deadTarget, graceProtected, corpseSpawned, room);

        roomService.clearPlayerLocation(deadTarget.getUsername());

        return new GameActionResult(null, deadTarget, messages);
    }

    private List<GameMessage> buildDeathMessages(
            @Nullable Player attacker, Player deadTarget, boolean graceProtected, boolean corpseSpawned,
            @Nullable Room room) {
        List<GameMessage> messages = new ArrayList<>();
        String targetName = deadTarget.getUsername().getValue();

        messages.add(GameMessage.toPlayer(deadTarget.getUsername(), "You have died."));
        messages.add(GameMessage.toPlayer(
            deadTarget.getUsername(),
            "You will awaken in " + resolveRespawnRoomName(deadTarget) + "."));
        if (graceProtected) {
            messages.add(GameMessage.toPlayer(
                deadTarget.getUsername(),
                "You keep your belongings this time - newbie grace ends at level "
                    + DeathSettings.graceLevel() + "."));
        } else if (corpseSpawned) {
            String where = room != null ? room.getName() : "where you fell";
            messages.add(GameMessage.toPlayer(
                deadTarget.getUsername(),
                "Your corpse lies in " + where + ", holding your gold and items. "
                    + "Type CORPSE to be walked back to it before it decays."));
        }

        if (attacker == null) {
            messages.add(GameMessage.toRoom(
                deadTarget.getUsername(), deadTarget.getUsername(),
                targetName + " has died."));
            return messages;
        }

        if (!attacker.getUsername().equals(deadTarget.getUsername())) {
            messages.add(GameMessage.toPlayer(
                attacker.getUsername(),
                "You have slain " + targetName + "."));
        }

        String roomMessage = attacker.getUsername().equals(deadTarget.getUsername())
            ? targetName + " has died."
            : targetName + " has been slain by " + attacker.getUsername().getValue() + "!";
        messages.add(GameMessage.toRoom(
            attacker.getUsername(), deadTarget.getUsername(),
            roomMessage));

        return messages;
    }

    /**
     * Resolves the display name of the room the dying player will actually respawn in, so the death
     * message and the respawn mechanic ({@link io.taanielo.jmud.core.player.PlayerRespawnTicker}) agree
     * on the destination. A player with a {@code BIND}-anchored respawn point ({@link Player#boundRoomId()},
     * issue #659) is told the bound room's real name; an unbound player falls back to the default
     * Training Yard's real display name. Never leaks a raw room id.
     *
     * @param deadTarget the player who just died
     * @return the human-readable name of the room they will awaken in
     */
    private String resolveRespawnRoomName(Player deadTarget) {
        RoomId boundRoom = boundRoomIdOf(deadTarget);
        if (boundRoom != null) {
            Optional<Room> resolved = roomService.findRoom(boundRoom);
            if (resolved.isPresent()) {
                return resolved.get().getName();
            }
        }
        return roomService.findRoom(RoomId.of(DeathSettings.RESPAWN_ROOM_ID))
            .map(Room::getName)
            .orElse(DeathSettings.RESPAWN_ROOM_ID);
    }

    private @Nullable RoomId boundRoomIdOf(Player source) {
        String bound = source.boundRoomId();
        return bound == null || bound.isBlank() ? null : RoomId.of(bound);
    }
}
