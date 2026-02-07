package io.taanielo.jmud.core.action;

import java.util.Objects;

import io.taanielo.jmud.core.authentication.Username;

/**
 * A message produced by a game action, tagged with its intended delivery target.
 *
 * <p>Messages are classified by {@link Type}: directed at the acting player
 * ({@code SOURCE}), a specific other player ({@code PLAYER}), or broadcast
 * to room occupants ({@code ROOM}).
 */
public record GameMessage(Type type, Username sourceExclude, Username targetExclude, String text) {

    /** Delivery target for a game message. */
    public enum Type {
        /** Message to the player who initiated the action. */
        SOURCE,
        /** Message to a specific other player identified by {@link #targetExclude}. */
        PLAYER,
        /** Message to room occupants, excluding source and target. */
        ROOM
    }

    public GameMessage {
        Objects.requireNonNull(type, "Message type is required");
        Objects.requireNonNull(text, "Message text is required");
    }

    /** Creates a message directed at the acting player. */
    public static GameMessage toSource(String text) {
        return new GameMessage(Type.SOURCE, null, null, text);
    }

    /** Creates a message directed at a specific player. */
    public static GameMessage toPlayer(Username target, String text) {
        Objects.requireNonNull(target, "Target username is required");
        return new GameMessage(Type.PLAYER, null, target, text);
    }

    /** Creates a message to room occupants, excluding source and target. */
    public static GameMessage toRoom(Username sourceExclude, Username targetExclude, String text) {
        return new GameMessage(Type.ROOM, sourceExclude, targetExclude, text);
    }
}
