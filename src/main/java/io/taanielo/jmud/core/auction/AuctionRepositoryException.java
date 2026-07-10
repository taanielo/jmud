package io.taanielo.jmud.core.auction;

/**
 * Thrown when auction data (house definitions or persisted listings) cannot be read from or written
 * to the underlying storage.
 */
public class AuctionRepositoryException extends Exception {

    public AuctionRepositoryException(String message) {
        super(message);
    }

    public AuctionRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
