package io.taanielo.jmud.core.quest;

/**
 * Thrown when the {@link QuestRepository} cannot load or access quest data.
 */
public class QuestRepositoryException extends Exception {

    public QuestRepositoryException(String message) {
        super(message);
    }

    public QuestRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
