package io.taanielo.jmud.core.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.craft.repository.json.JsonRecipeRepository;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.mob.repository.json.JsonMobTemplateRepository;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Integration smoke-test confirming the alchemy content ships correctly: the alchemist NPC is
 * tagged so the {@code BREW} room check can find it, and the alchemy recipe set loads from its own
 * subdirectory without leaking into the blacksmith's top-level recipe set.
 */
class AlchemyContentTest {

    @Test
    void alchemistMob_isTaggedAndPlaced() throws RepositoryException {
        JsonMobTemplateRepository repo = new JsonMobTemplateRepository(Path.of("data"));

        MobTemplate alchemist = repo.findAll().stream()
            .filter(t -> "alchemist".equals(t.id().getValue()))
            .findFirst()
            .orElse(null);

        assertNotNull(alchemist, "Alchemist template must be present");
        assertTrue(alchemist.hasTag("alchemist"), "Alchemist must carry the 'alchemist' tag");
        assertFalse(alchemist.aggressive(), "Alchemist should not be aggressive");
        assertEquals(9999, alchemist.maxHp(), "Alchemist should be unkillable-in-practice");
        assertEquals("darkwood-trail", alchemist.spawnRoomId().getValue());
    }

    @Test
    void alchemyRecipes_loadFromDedicatedSubdirectory() throws RecipeRepositoryException {
        JsonRecipeRepository alchemyRepo = new JsonRecipeRepository(Path.of("data"), "recipes/alchemy");

        List<Recipe> recipes = alchemyRepo.findAll();

        assertTrue(recipes.size() >= 3, "Expected at least three alchemy recipes, got " + recipes.size());
        for (Recipe recipe : recipes) {
            boolean usesWildHerbs = recipe.materials().stream()
                .anyMatch(m -> m.itemId().equals(ItemId.of("wild-herbs")));
            assertTrue(usesWildHerbs, "Recipe " + recipe.id().value() + " should consume wild-herbs");
        }
    }

    @Test
    void blacksmithRecipes_doNotIncludeAlchemyRecipes() throws RecipeRepositoryException {
        JsonRecipeRepository blacksmithRepo = new JsonRecipeRepository(Path.of("data"));

        Set<String> ids = blacksmithRepo.findAll().stream()
            .map(r -> r.id().value())
            .collect(Collectors.toSet());

        assertFalse(ids.contains("alchemy-health-potion"),
            "Alchemy recipes must not leak into the blacksmith recipe set");
    }
}
