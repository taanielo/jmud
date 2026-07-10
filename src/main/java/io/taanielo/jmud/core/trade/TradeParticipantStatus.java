package io.taanielo.jmud.core.trade;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.world.RoomId;

/**
 * A point-in-time snapshot of a trade participant's world state, used by
 * {@link TradeService#tick()} to decide whether a live session must be auto-cancelled.
 *
 * @param online   whether the player currently has a connected, in-world session
 * @param room     the player's current room, or {@code null} when offline or unlocated
 * @param dead     whether the player is currently dead
 * @param inCombat whether the player is engaged in combat (mob combat or a duel)
 */
public record TradeParticipantStatus(boolean online, @Nullable RoomId room, boolean dead, boolean inCombat) {

    /** A status representing a player who is offline / has no live session. */
    public static final TradeParticipantStatus OFFLINE = new TradeParticipantStatus(false, null, false, false);
}
