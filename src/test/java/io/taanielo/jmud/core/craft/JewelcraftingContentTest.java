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
import io.taanielo.jmud.core.gathering.ResourceNode;
import io.taanielo.jmud.core.gathering.repository.json.JsonResourceNodeRepository;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.mob.repository.json.JsonMobTemplateRepository;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.json.JsonItemRepository;

/**
 * Integration smoke-test confirming the jewelcrafting content ships correctly: the jeweler NPC is
 * tagged so the {@code CUT} room check can find it, the jewelcrafting recipe set loads from its own
 * subdirectory without leaking into the blacksmith set, the raw gems are gatherable from real nodes,
 * and at least one recipe is gated behind a {@code min_skill} so the profession has room to grow.
 */
class JewelcraftingContentTest {

    @Test
    void jewelerMob_isTaggedAndPlaced() throws RepositoryException {
        JsonMobTemplateRepository repo = new JsonMobTemplateRepository(Path.of("data"));

        MobTemplate jeweler = repo.findAll().stream()
            .filter(t -> "jeweler".equals(t.id().getValue()))
            .findFirst()
            .orElse(null);

        assertNotNull(jeweler, "Jeweler template must be present");
        assertTrue(jeweler.hasTag("jeweler"), "Jeweler must carry the 'jeweler' tag");
        assertTrue(jeweler.hasTag("shopkeeper"), "Jeweler must carry the 'shopkeeper' tag");
        assertFalse(jeweler.aggressive(), "Jeweler should not be aggressive");
        assertEquals(9999, jeweler.maxHp(), "Jeweler should be unkillable-in-practice");
        assertEquals("crumbling-courtyard", jeweler.spawnRoomId().getValue());
    }

    @Test
    void jewelcraftingRecipes_loadFromDedicatedSubdirectory() throws RecipeRepositoryException {
        JsonRecipeRepository repo = new JsonRecipeRepository(Path.of("data"), "recipes/jewelcrafting");

        List<Recipe> recipes = repo.findAll();

        assertTrue(recipes.size() >= 5,
            "Expected at least five jewelcrafting recipes, got " + recipes.size());
    }

    @Test
    void jewelcraftingRecipes_haveAtLeastOneMinSkillGate() throws RecipeRepositoryException {
        JsonRecipeRepository repo = new JsonRecipeRepository(Path.of("data"), "recipes/jewelcrafting");

        boolean gated = repo.findAll().stream().anyMatch(r -> r.minSkill() > 0);

        assertTrue(gated, "At least one jewelcrafting recipe must be gated behind min_skill");
    }

    @Test
    void jewelcraftingRecipes_doNotLeakIntoBlacksmithSet() throws RecipeRepositoryException {
        JsonRecipeRepository blacksmithRepo = new JsonRecipeRepository(Path.of("data"));

        boolean leaked = blacksmithRepo.findAll().stream()
            .anyMatch(r -> r.id().value().startsWith("jewelcrafting-"));

        assertFalse(leaked, "Jewelcrafting recipes must not leak into the blacksmith recipe set");
    }

    @Test
    void rawGems_areGatherableFromRealNodes() throws Exception {
        JsonResourceNodeRepository repo = new JsonResourceNodeRepository(Path.of("data"));

        List<ResourceNode> nodes = repo.findAll();

        assertTrue(nodes.stream().anyMatch(n -> "rough-quartz".equals(n.yieldItemId().getValue())),
            "A resource node must yield rough-quartz");
        assertTrue(nodes.stream().anyMatch(n -> "raw-garnet".equals(n.yieldItemId().getValue())),
            "A resource node must yield raw-garnet");
    }

    @Test
    void starcutHeartgem_isNeckAccessoryWithCasterMana() throws RepositoryException {
        JsonItemRepository itemRepository = new JsonItemRepository(Path.of("data"));

        Optional<Item> heartgemOpt = itemRepository.findById(ItemId.of("starcut-heartgem"));

        assertTrue(heartgemOpt.isPresent(), "Starcut Heartgem item must exist");
        Item heartgem = heartgemOpt.get();
        assertEquals(EquipmentSlot.NECK, heartgem.getEquipSlot(),
            "Starcut Heartgem must equip in the neck slot");
        assertTrue(heartgem.getAttributes().getStats().getOrDefault("mana", 0) > 0,
            "Starcut Heartgem must provide mana for casters");
    }

    @Test
    void quartzBand_isFingerAccessory() throws RepositoryException {
        JsonItemRepository itemRepository = new JsonItemRepository(Path.of("data"));

        Optional<Item> bandOpt = itemRepository.findById(ItemId.of("quartz-band"));

        assertTrue(bandOpt.isPresent(), "Quartz Band item must exist");
        assertEquals(EquipmentSlot.FINGER, bandOpt.get().getEquipSlot(),
            "Quartz Band must equip in the finger slot");
    }
}
