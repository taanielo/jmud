package io.taanielo.jmud.core.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.craft.repository.json.JsonRecipeRepository;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.mob.repository.json.JsonMobTemplateRepository;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.json.JsonItemRepository;

/**
 * Integration smoke-test confirming the cooking content ships correctly: the cook NPC is tagged so
 * the {@code COOK} room check can find it, the cooking recipe set loads from its own subdirectory,
 * and at least one cooked meal carries a timed buff effect that {@code EAT} can apply.
 */
class CookingContentTest {

    @Test
    void cookMob_isTaggedAndPlaced() throws RepositoryException {
        JsonMobTemplateRepository repo = new JsonMobTemplateRepository(Path.of("data"));

        MobTemplate cook = repo.findAll().stream()
            .filter(t -> "cook".equals(t.id().getValue()))
            .findFirst()
            .orElse(null);

        assertNotNull(cook, "Cook template must be present");
        assertTrue(cook.hasTag("cook"), "Cook must carry the 'cook' tag");
        assertTrue(cook.hasTag("shopkeeper"), "Cook must carry the 'shopkeeper' tag");
        assertFalse(cook.aggressive(), "Cook should not be aggressive");
        assertEquals(9999, cook.maxHp(), "Cook should be unkillable-in-practice");
        assertEquals("courtyard", cook.spawnRoomId().getValue());
    }

    @Test
    void cookingRecipes_loadFromDedicatedSubdirectory() throws RecipeRepositoryException {
        JsonRecipeRepository cookingRepo = new JsonRecipeRepository(Path.of("data"), "recipes/cooking");

        List<Recipe> recipes = cookingRepo.findAll();

        assertTrue(recipes.size() >= 3, "Expected at least three cooking recipes, got " + recipes.size());
    }

    @Test
    void cookingRecipes_doNotLeakIntoBlacksmithSet() throws RecipeRepositoryException {
        JsonRecipeRepository blacksmithRepo = new JsonRecipeRepository(Path.of("data"));

        boolean leaked = blacksmithRepo.findAll().stream()
            .anyMatch(r -> r.id().value().startsWith("cooking-"));

        assertFalse(leaked, "Cooking recipes must not leak into the blacksmith recipe set");
    }

    @Test
    void heartyStewMeal_restoresHungerAndCarriesBuffEffect() throws RepositoryException {
        JsonItemRepository itemRepository = new JsonItemRepository(Path.of("data"));

        Optional<Item> stewOpt = itemRepository.findById(ItemId.of("hearty-stew"));

        assertTrue(stewOpt.isPresent(), "Hearty Stew meal item must exist");
        Item stew = stewOpt.get();
        assertTrue(stew.getAttributes().getStats().getOrDefault("hunger", 0) > 0,
            "Hearty Stew must restore hunger");
        assertFalse(stew.getEffects().isEmpty(), "Hearty Stew must carry a timed buff effect");
    }
}
