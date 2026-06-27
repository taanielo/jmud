package io.taanielo.jmud.core.shop;

/** Thrown when shop data cannot be loaded from persistent storage. */
public class ShopRepositoryException extends Exception {

    public ShopRepositoryException(String message) {
        super(message);
    }

    public ShopRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
