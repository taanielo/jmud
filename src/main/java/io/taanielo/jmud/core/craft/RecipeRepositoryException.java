package io.taanielo.jmud.core.craft;

/** Thrown when crafting recipe data cannot be loaded from persistent storage. */
public class RecipeRepositoryException extends Exception {

    public RecipeRepositoryException(String message) {
        super(message);
    }

    public RecipeRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
