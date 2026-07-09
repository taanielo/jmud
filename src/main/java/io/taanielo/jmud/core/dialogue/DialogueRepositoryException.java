package io.taanielo.jmud.core.dialogue;

/**
 * Thrown when the {@link DialogueRepository} cannot load or access dialogue data.
 */
public class DialogueRepositoryException extends Exception {

    public DialogueRepositoryException(String message) {
        super(message);
    }

    public DialogueRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
