package io.taanielo.jmud.core.bank;

/**
 * Thrown when bank data cannot be read from the underlying storage.
 */
public class BankRepositoryException extends Exception {

    public BankRepositoryException(String message) {
        super(message);
    }

    public BankRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
