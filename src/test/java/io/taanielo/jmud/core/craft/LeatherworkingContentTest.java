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
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.json.JsonItemRepository;

/**
 * Integration smoke-test confirming the leatherworking content ships correctly: the tanner NPC is
 * tagged so the {@code TAN} room check can find it, the leatherworking recipe set loads from its own
 * subdirectory without leaking into the blacksmith set, and at least one recipe is gated behind a
 * {@code min_skill} so the profession has room to grow.
 */
class LeatherworkingContentTest {

    @Test
    void tannerMob_isTaggedAndPlaced() throws RepositoryException {
        JsonMobTemplateRepository repo = new JsonMobTemplateRepository(Path.of("data"));

        MobTemplate tanner = repo.findAll().stream()
            .filter(t -> "tanner".equals(t.id().getValue()))
            .findFirst()
            .orElse(null);

        assertNotNull(tanner, "Tanner template must be present");
        assertTrue(tanner.hasTag("leatherworker"), "Tanner must carry the 'leatherworker' tag");
        assertTrue(tanner.hasTag("shopkeeper"), "Tanner must carry the 'shopkeeper' tag");
        assertFalse(tanner.aggressive(), "Tanner should not be aggressive");
        assertEquals(9999, tanner.maxHp(), "Tanner should be unkillable-in-practice");
        assertEquals("tangled-undergrowth", tanner.spawnRoomId().getValue());
    }

    @Test
    void leatherworkingRecipes_loadFromDedicatedSubdirectory() throws RecipeRepositoryException {
        JsonRecipeRepository repo = new JsonRecipeRepository(Path.of("data"), "recipes/leatherworking");

        List<Recipe> recipes = repo.findAll();

        assertTrue(recipes.size() >= 5,
            "Expected at least five leatherworking recipes, got " + recipes.size());
    }

    @Test
    void leatherworkingRecipes_haveAtLeastOneMinSkillGate() throws RecipeRepositoryException {
        JsonRecipeRepository repo = new JsonRecipeRepository(Path.of("data"), "recipes/leatherworking");

        boolean gated = repo.findAll().stream().anyMatch(r -> r.minSkill() > 0);

        assertTrue(gated, "At least one leatherworking recipe must be gated behind min_skill");
    }

    @Test
    void leatherworkingRecipes_doNotLeakIntoBlacksmithSet() throws RecipeRepositoryException {
        JsonRecipeRepository blacksmithRepo = new JsonRecipeRepository(Path.of("data"));

        boolean leaked = blacksmithRepo.findAll().stream()
            .anyMatch(r -> r.id().value().startsWith("leatherworking-"));

        assertFalse(leaked, "Leatherworking recipes must not leak into the blacksmith recipe set");
    }

    @Test
    void direhideCowl_isLightArmorWithHeadSlot() throws RepositoryException {
        JsonItemRepository itemRepository = new JsonItemRepository(Path.of("data"));

        Optional<Item> cowlOpt = itemRepository.findById(ItemId.of("direhide-cowl"));

        assertTrue(cowlOpt.isPresent(), "Direhide Cowl item must exist");
        Item cowl = cowlOpt.get();
        assertEquals(EquipmentSlot.HEAD, cowl.getEquipSlot(), "Direhide Cowl must equip in the head slot");
        assertTrue(cowl.getAttributes().getStats().getOrDefault("ac", 0) > 0,
            "Direhide Cowl must provide armor");
    }
}
