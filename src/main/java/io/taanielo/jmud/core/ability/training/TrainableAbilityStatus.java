package io.taanielo.jmud.core.ability.training;

import io.taanielo.jmud.core.ability.Ability;

/**
 * One row of the {@code TRAIN LIST} view: an ability in the player's class pool annotated
 * with the player's current relationship to it.
 *
 * @param ability the trainable ability
 * @param status  whether the player has learned it, may train it now, or is still too low level
 */
public record TrainableAbilityStatus(Ability ability, Status status) {

    /**
     * The player's standing with a trainable ability.
     */
    public enum Status {
        /** Already learned; nothing to spend. */
        LEARNED,
        /** Not yet learned and the player meets the level requirement. */
        AVAILABLE,
        /** Not yet learned but the player is below the ability's required level. */
        REQUIRES_LEVEL
    }
}
