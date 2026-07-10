package io.taanielo.jmud.core.craft;

import java.util.List;

/**
 * Data-access contract for {@link Recipe} definitions.
 */
public interface RecipeRepository {

    /**
     * Returns all crafting recipe definitions.
     *
     * @return every known recipe; never {@code null}
     * @throws RecipeRepositoryException when data cannot be read
     */
    List<Recipe> findAll() throws RecipeRepositoryException;
}
