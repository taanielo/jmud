package io.taanielo.jmud.core.combat.repository;

public class AttackRepositoryException extends Exception {
    public AttackRepositoryException(String message) {
        super(message);
    }

    public AttackRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
