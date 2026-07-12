package io.taanielo.jmud.core.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.craft.repository.json.JsonRecipeRepository;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Rarity;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.json.JsonItemRepository;

/**
 * Content smoke-test confirming the Cinderforged Cuirass closes the demoncore-cinder crafting loop:
 * the top-level blacksmith recipe loads, consumes demoncore-cinder, is gated behind the new top
 * blacksmithing tier, and produces a chest-slot piece that slots between the Iron-Forged and Scarplate
 * armor tiers.
 */
class BlacksmithCinderforgedContentTest {

    private static final ItemId DEMONCORE_CINDER = ItemId.of("demoncore-cinder");
    private static final ItemId CINDERFORGED_CUIRASS = ItemId.of("cinderforged-cuirass");

    @Test
    void cinderforgedRecipe_loadsAndConsumesDemoncoreCinderAtNewTopTier() throws RecipeRepositoryException {
        JsonRecipeRepository blacksmithRepo = new JsonRecipeRepository(Path.of("data"));

        Recipe recipe = blacksmithRepo.findAll().stream()
            .filter(r -> r.id().value().equals("cinderforged-cuirass"))
            .findFirst()
            .orElse(null);

        assertNotNull(recipe, "Cinderforged Cuirass recipe must load from the blacksmith recipe set");
        assertEquals(CINDERFORGED_CUIRASS, recipe.outputItemId());
        assertTrue(recipe.materials().stream().anyMatch(m -> m.itemId().equals(DEMONCORE_CINDER)),
            "Recipe must consume demoncore-cinder to close the Cinder Reaches loot loop");
        assertTrue(recipe.minSkill() >= 3,
            "Recipe must be gated above the previous game-wide max min_skill of 2, got " + recipe.minSkill());
        assertTrue(recipe.goldCost() > 0, "Recipe should carry a meaningful gold cost");
    }

    @Test
    void cinderforgedCuirass_isUncommonChestArmorBetweenIronForgedAndScarplate() throws RepositoryException {
        JsonItemRepository itemRepository = new JsonItemRepository(Path.of("data"));

        Item cuirass = itemRepository.findById(CINDERFORGED_CUIRASS).orElse(null);

        assertNotNull(cuirass, "Cinderforged Cuirass item must load");
        assertEquals(EquipmentSlot.CHEST, cuirass.getEquipSlot());
        assertEquals(Rarity.UNCOMMON, cuirass.getRarity());
        int ac = cuirass.getAttributes().getStats().getOrDefault("ac", 0);
        assertTrue(ac > 8 && ac < 12,
            "AC must sit between Iron-Forged (8) and Scarplate of Embers (12), got " + ac);
    }
}
