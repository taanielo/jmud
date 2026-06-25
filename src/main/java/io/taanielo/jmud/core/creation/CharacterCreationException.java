package io.taanielo.jmud.core.creation;

/**
 * Thrown when character-creation data cannot be loaded or is invalid.
 */
public class CharacterCreationException extends Exception {

    public CharacterCreationException(String message) {
        super(message);
    }

    public CharacterCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
