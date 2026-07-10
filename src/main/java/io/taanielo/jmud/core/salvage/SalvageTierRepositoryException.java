package io.taanielo.jmud.core.salvage;

/** Thrown when salvage tier data cannot be loaded from persistent storage. */
public class SalvageTierRepositoryException extends Exception {

    public SalvageTierRepositoryException(String message) {
        super(message);
    }

    public SalvageTierRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
