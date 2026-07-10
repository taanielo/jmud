package io.taanielo.jmud.core.enchant;

import java.util.List;

/**
 * Read-only access to the data-driven enchanting recipe definitions (see
 * {@code data/recipes/enchanting/*.json}). Implementations load recipes from persistent storage;
 * domain services depend on this interface rather than any concrete loader (AGENTS.md §3.3).
 */
public interface EnchantRecipeRepository {

    /**
     * Returns every defined enchanting recipe.
     *
     * @return all enchant recipes, never null (possibly empty)
     * @throws EnchantRecipeRepositoryException if the recipe data cannot be read
     */
    List<EnchantRecipe> findAll() throws EnchantRecipeRepositoryException;
}
