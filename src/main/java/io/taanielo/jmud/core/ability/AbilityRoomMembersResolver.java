package io.taanielo.jmud.core.ability;

import java.util.List;

import io.taanielo.jmud.core.player.Player;

/**
 * Resolves all players currently present in the same room as the source player,
 * including the source themselves.
 *
 * <p>Used by {@link AbilityEngine} to support {@link AbilityTargeting#BENEFICIAL_GROUP}
 * abilities that apply effects to every room occupant.
 */
@FunctionalInterface
public interface AbilityRoomMembersResolver {

    /**
     * Returns every player in the same room as {@code source}, including the source.
     *
     * @param source the player whose room is queried
     * @return non-null, possibly empty list of players in the room
     */
    List<Player> resolveRoomMembers(Player source);
}
