package io.taanielo.jmud.core.action;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.RoomId;

/**
 * A message produced by a game action, tagged with its intended delivery target.
 *
 * <p>Messages are classified by {@link Type}: directed at the acting player
 * ({@code SOURCE}), a specific other player ({@code PLAYER}), room occupants
 * resolved from the acting player's current room ({@code ROOM}), or room
 * occupants of an explicitly named room ({@code ROOM_AT}) — used when the
 * acting player's room has already changed by delivery time (e.g. recall).
 */
public record GameMessage(
    Type type,
    @Nullable Username sourceExclude,
    @Nullable Username targetExclude,
    String text,
    @Nullable RoomId roomId
) {

    /** Delivery target for a game message. */
    public enum Type {
        /** Message to the player who initiated the action. */
        SOURCE,
        /** Message to a specific other player identified by {@link #targetExclude}. */
        PLAYER,
        /** Message to room occupants, excluding source and target. */
        ROOM,
        /** Message to occupants of an explicit room, excluding {@link #targetExclude}. */
        ROOM_AT
    }

    public GameMessage {
        Objects.requireNonNull(type, "Message type is required");
        Objects.requireNonNull(text, "Message text is required");
    }

    /** Creates a message directed at the acting player. */
    public static GameMessage toSource(String text) {
        return new GameMessage(Type.SOURCE, null, null, text, null);
    }

    /** Creates a message directed at a specific player. */
    public static GameMessage toPlayer(Username target, String text) {
        Objects.requireNonNull(target, "Target username is required");
        return new GameMessage(Type.PLAYER, null, target, text, null);
    }

    /** Creates a message to room occupants, excluding source and target. */
    public static GameMessage toRoom(Username sourceExclude, Username targetExclude, String text) {
        return new GameMessage(Type.ROOM, sourceExclude, targetExclude, text, null);
    }

    /**
     * Creates a message to the occupants of an explicit room, excluding the given player.
     *
     * <p>Unlike {@link #toRoom}, the target room is resolved at message-construction time
     * rather than from the acting player's current location at delivery time. This is
     * required when the acting player has already moved out of that room (e.g. a recall
     * departure message, or an arrival message for a room the player already occupies).
     *
     * @param roomId the room whose occupants should receive the message
     * @param exclude the player to exclude from delivery (typically the acting player)
     * @param text the message text
     */
    public static GameMessage toRoomAt(RoomId roomId, Username exclude, String text) {
        Objects.requireNonNull(roomId, "Room id is required");
        return new GameMessage(Type.ROOM_AT, null, exclude, text, roomId);
    }
}
