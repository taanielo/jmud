package io.taanielo.jmud.core.creation;

import io.taanielo.jmud.core.character.RaceId;

/**
 * Represents the in-progress state of a character-creation flow.
 *
 * <p>A newly created player starts in {@link ChoosingRace}; after a valid race
 * is chosen they move to {@link ChoosingClass}; once a class is confirmed the
 * flow is complete and the client enters normal gameplay.
 */
public sealed interface CharacterCreationState
    permits CharacterCreationState.ChoosingRace, CharacterCreationState.ChoosingClass {

    /** The player has not yet chosen a race. */
    record ChoosingRace() implements CharacterCreationState {}

    /**
     * The player has chosen a race and is now choosing a class.
     *
     * @param chosenRace the race the player selected
     */
    record ChoosingClass(RaceId chosenRace) implements CharacterCreationState {}
}
