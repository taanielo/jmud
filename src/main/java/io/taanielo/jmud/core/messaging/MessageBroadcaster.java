package io.taanielo.jmud.core.messaging;

import java.util.Set;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Scoped message delivery service: player-, room-, and server-wide broadcast.
 *
 * <p>This is the only sanctioned fan-out mechanism for delivering {@link Message}s to
 * connected clients (AGENTS.md §3.3); adapters (e.g. the socket layer) must route all
 * multi-recipient delivery through here instead of hand-rolling loops over connected
 * clients.
 */
public interface MessageBroadcaster {

    /**
     * Delivers a message to a single online player, identified by username.
     *
     * <p>This is a no-op (and does not throw) when the target player is not currently
     * connected.
     *
     * @param target  the recipient's username
     * @param message the message to deliver
     */
    void sendToPlayer(Username target, Message message);

    /**
     * Delivers a message to every online player currently located in the given room.
     *
     * @param room    the room whose occupants should receive the message
     * @param message the message to deliver
     * @param exclude usernames to skip even if present in the room (e.g. the speaker);
     *                may be empty, never {@code null}
     */
    void broadcastToRoom(RoomId room, Message message, Set<Username> exclude);

    /**
     * Delivers a message to every online player, regardless of location.
     *
     * @param message the message to deliver
     * @param exclude usernames to skip (e.g. the sender); may be empty, never {@code null}
     */
    void broadcastGlobal(Message message, Set<Username> exclude);
}
