package io.taanielo.jmud.core.ability;

import io.taanielo.jmud.core.player.Player;

/**
 * Checks whether a target player/mob carries a specific classification tag.
 *
 * <p>Used by {@link AbilityEngine} to validate {@link AbilityTargeting#HARMFUL_UNDEAD}
 * abilities: the engine queries this checker to confirm that the resolved target
 * has the {@code "undead"} tag before applying the ability's effects.
 */
@FunctionalInterface
public interface AbilityMobTagChecker {

    /**
     * Returns {@code true} when {@code target} carries the given tag.
     *
     * @param target the resolved ability target
     * @param tag    the tag to check (e.g. {@code "undead"})
     * @return {@code true} if the target has the tag; {@code false} otherwise
     */
    boolean hasTag(Player target, String tag);
}
