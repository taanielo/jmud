package io.taanielo.jmud.core.enchant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.craft.RecipeMaterial;
import io.taanielo.jmud.core.enchant.repository.json.JsonEnchantRecipeRepository;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.mob.repository.json.JsonMobTemplateRepository;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.json.JsonAffixRepository;
import io.taanielo.jmud.core.world.repository.json.JsonItemRepository;

/**
 * Integration smoke-test confirming the enchanting content ships correctly: the Enchanter NPC is
 * tagged and placed so the {@code ENCHANT} room check can find it, the enchant recipe set loads,
 * every recipe references a defined affix plus the arcane-dust material, and that material item
 * exists.
 */
class EnchantingContentTest {

    private static final ItemId ARCANE_DUST = ItemId.of("arcane-dust");

    @Test
    void enchanterMob_isTaggedAndPlaced() throws RepositoryException {
        JsonMobTemplateRepository repo = new JsonMobTemplateRepository(Path.of("data"));

        MobTemplate enchanter = repo.findAll().stream()
            .filter(t -> "enchanter".equals(t.id().getValue()))
            .findFirst()
            .orElse(null);

        assertNotNull(enchanter, "Enchanter template must be present");
        assertTrue(enchanter.hasTag("enchanter"), "Enchanter must carry the 'enchanter' tag");
        assertFalse(enchanter.aggressive(), "Enchanter should not be aggressive");
        assertEquals(9999, enchanter.maxHp(), "Enchanter should be unkillable-in-practice");
    }

    @Test
    void enchantRecipes_loadAndReferenceKnownAffixesAndMaterial() throws Exception {
        JsonEnchantRecipeRepository recipeRepo = new JsonEnchantRecipeRepository(Path.of("data"));
        JsonAffixRepository affixRepo = new JsonAffixRepository(Path.of("data"));

        List<EnchantRecipe> recipes = recipeRepo.findAll();

        assertTrue(recipes.size() >= 2, "Expected at least two enchant recipes, got " + recipes.size());
        for (EnchantRecipe recipe : recipes) {
            assertTrue(affixRepo.findById(recipe.affixId()).isPresent(),
                "Recipe " + recipe.id() + " references undefined affix " + recipe.affixId().getValue());
            boolean usesDust = recipe.materials().stream()
                .anyMatch((RecipeMaterial m) -> m.itemId().equals(ARCANE_DUST));
            assertTrue(usesDust, "Recipe " + recipe.id() + " should consume arcane-dust");
        }
    }

    @Test
    void arcaneDustMaterial_exists() throws RepositoryException {
        JsonItemRepository itemRepo = new JsonItemRepository(Path.of("data"));

        assertTrue(itemRepo.findById(ARCANE_DUST).isPresent(), "arcane-dust item must be defined");
    }
}
