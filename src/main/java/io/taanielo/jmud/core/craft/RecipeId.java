package io.taanielo.jmud.core.craft;

import java.util.Locale;
import java.util.Objects;

/**
 * Value object wrapping a crafting recipe identifier (e.g. {@code wolf-pelt-cloak}).
 *
 * @param value the non-blank recipe id
 */
public record RecipeId(String value) {

    public RecipeId {
        Objects.requireNonNull(value, "Recipe id is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Recipe id must not be blank");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Creates a recipe id from the given raw string.
     *
     * @param value the recipe id value
     * @return the recipe id value object
     */
    public static RecipeId of(String value) {
        return new RecipeId(value);
    }
}
