package io.taanielo.jmud.core.enchant;

/** Thrown when enchanting recipe data cannot be loaded from persistent storage. */
public class EnchantRecipeRepositoryException extends Exception {

    public EnchantRecipeRepositoryException(String message) {
        super(message);
    }

    public EnchantRecipeRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
