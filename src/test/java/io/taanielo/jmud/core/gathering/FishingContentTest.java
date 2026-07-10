package io.taanielo.jmud.core.gathering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.craft.Recipe;
import io.taanielo.jmud.core.craft.RecipeRepositoryException;
import io.taanielo.jmud.core.craft.repository.json.JsonRecipeRepository;
import io.taanielo.jmud.core.gathering.repository.json.JsonResourceNodeRepository;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.json.JsonItemRepository;

/**
 * Integration smoke-test confirming the fishing content ships correctly: fishing resource nodes are
 * placed at the water/dock rooms, each yields a distinct raw-fish crafting material (no hunger or
 * eat messages), and the fish feed a cooking recipe whose cooked output carries a timed buff effect.
 */
class FishingContentTest {

    private static final Set<String> FISH_ITEM_IDS = Set.of(
        "silver-trout", "reef-eel", "deckside-mackerel", "fogfin-cod", "pearl-perch");

    @Test
    void fishingNodes_arePlacedAtWaterRooms() throws RepositoryException {
        List<ResourceNode> nodes = new JsonResourceNodeRepository(Path.of("data")).findAll();

        List<ResourceNode> fishingNodes = nodes.stream()
            .filter(n -> FISH_ITEM_IDS.contains(n.yieldItemId().getValue()))
            .toList();

        assertTrue(fishingNodes.size() >= 3,
            "Expected at least three fishing nodes, got " + fishingNodes.size());

        Set<String> rooms = fishingNodes.stream().map(n -> n.roomId().getValue()).collect(Collectors.toSet());
        Set<String> waterRooms = Set.of(
            "north-dock", "south-dock", "coastal-ferry-deck", "shrouded-isle-shore", "shrouded-isle-cove");
        assertTrue(waterRooms.containsAll(rooms),
            "Fishing nodes must sit in water/dock rooms, found " + rooms);

        long distinctYields = fishingNodes.stream().map(n -> n.yieldItemId().getValue()).distinct().count();
        assertTrue(distinctYields == fishingNodes.size(), "Each fishing node must yield a distinct fish");
    }

    @Test
    void rawFish_areCraftingMaterials() throws RepositoryException {
        JsonItemRepository itemRepository = new JsonItemRepository(Path.of("data"));

        for (String fishId : FISH_ITEM_IDS) {
            Optional<Item> fishOpt = itemRepository.findById(ItemId.of(fishId));
            assertTrue(fishOpt.isPresent(), "Raw fish item must exist: " + fishId);
            Item fish = fishOpt.get();
            assertTrue(fish.getAttributes().getStats().getOrDefault("hunger", 0) == 0,
                fishId + " is a raw material and must not restore hunger");
            assertTrue(fish.getEffects().isEmpty(), fishId + " must carry no effects");
            assertTrue(fish.getMessages().isEmpty(), fishId + " must carry no eat messages");
        }
    }

    @Test
    void fishRecipe_producesBuffMeal() throws RecipeRepositoryException, RepositoryException {
        JsonRecipeRepository cookingRepo = new JsonRecipeRepository(Path.of("data"), "recipes/cooking");
        JsonItemRepository itemRepository = new JsonItemRepository(Path.of("data"));

        List<Recipe> fishRecipes = cookingRepo.findAll().stream()
            .filter(r -> r.materials().stream().anyMatch(m -> FISH_ITEM_IDS.contains(m.itemId().getValue())))
            .toList();

        assertFalse(fishRecipes.isEmpty(), "Expected at least one cooking recipe that consumes a raw fish");

        Recipe recipe = fishRecipes.getFirst();
        Optional<Item> mealOpt = itemRepository.findById(recipe.outputItemId());
        assertTrue(mealOpt.isPresent(), "Fish recipe output meal must exist");
        Item meal = mealOpt.get();
        assertTrue(meal.getAttributes().getStats().getOrDefault("hunger", 0) > 0,
            "Cooked fish meal must restore hunger");
        assertFalse(meal.getEffects().isEmpty(), "Cooked fish meal must carry a timed buff effect");
    }
}
