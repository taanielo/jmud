package io.taanielo.jmud.core.character.repository;

public class ClassRepositoryException extends Exception {
    public ClassRepositoryException(String message) {
        super(message);
    }

    public ClassRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
