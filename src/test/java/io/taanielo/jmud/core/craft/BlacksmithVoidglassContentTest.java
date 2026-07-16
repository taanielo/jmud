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
 * Content smoke-test confirming the Voidglass Focus closes the voidglass-shard crafting loop: the
 * top-level blacksmith recipe loads, consumes voidglass-shard, is gated behind a new game-first
 * {@code min_skill: 4} tier (giving blacksmithing proficiency somewhere past level 3 to grind
 * toward), and produces a caster-leaning off-hand focus distinct from existing gear.
 */
class BlacksmithVoidglassContentTest {

    private static final ItemId VOIDGLASS_SHARD = ItemId.of("voidglass-shard");
    private static final ItemId VOIDGLASS_FOCUS = ItemId.of("voidglass-focus");

    @Test
    void voidglassFocusRecipe_loadsAndConsumesVoidglassShardAtNewTopTier() throws RecipeRepositoryException {
        JsonRecipeRepository blacksmithRepo = new JsonRecipeRepository(Path.of("data"));

        Recipe recipe = blacksmithRepo.findAll().stream()
            .filter(r -> r.id().value().equals("voidglass-focus"))
            .findFirst()
            .orElse(null);

        assertNotNull(recipe, "Voidglass Focus recipe must load from the blacksmith recipe set");
        assertEquals(VOIDGLASS_FOCUS, recipe.outputItemId());
        assertTrue(recipe.materials().stream().anyMatch(m -> m.itemId().equals(VOIDGLASS_SHARD)),
            "Recipe must consume voidglass-shard to close the Voidscar loot loop");
        assertEquals(4, recipe.minSkill(),
            "Recipe must be the game's first min_skill 4 tier, got " + recipe.minSkill());
        assertTrue(recipe.goldCost() > 0, "Recipe should carry a meaningful gold cost");
    }

    @Test
    void voidglassFocus_isAnUncommonCasterOffhand() throws RepositoryException {
        JsonItemRepository itemRepository = new JsonItemRepository(Path.of("data"));

        Item focus = itemRepository.findById(VOIDGLASS_FOCUS).orElse(null);

        assertNotNull(focus, "Voidglass Focus item must load");
        assertEquals(EquipmentSlot.OFFHAND, focus.getEquipSlot());
        assertEquals(Rarity.UNCOMMON, focus.getRarity());
        int intellect = focus.getAttributes().getStats().getOrDefault("intellect", 0);
        int mana = focus.getAttributes().getStats().getOrDefault("mana", 0);
        assertTrue(intellect > 0, "Focus must carry a caster-leaning intellect bonus, got " + intellect);
        assertTrue(mana > 0, "Focus must carry a caster-leaning mana bonus, got " + mana);
    }
}
