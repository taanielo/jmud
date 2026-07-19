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
 * Integration smoke-test confirming the tailoring content ships correctly: the tailor NPC is tagged so
 * the {@code SEW} room check can find it, the tailoring recipe set loads from its own subdirectory
 * without leaking into the blacksmith set, spider silk drops from the giant spider and grave linen is
 * gatherable from a real node, and at least one recipe is gated behind a {@code min_skill}.
 */
class TailoringContentTest {

    @Test
    void tailorMob_isTaggedAndPlaced() throws RepositoryException {
        JsonMobTemplateRepository repo = new JsonMobTemplateRepository(Path.of("data"));

        MobTemplate tailor = repo.findAll().stream()
            .filter(t -> "tailor".equals(t.id().getValue()))
            .findFirst()
            .orElse(null);

        assertNotNull(tailor, "Tailor template must be present");
        assertTrue(tailor.hasTag("tailor"), "Tailor must carry the 'tailor' tag");
        assertTrue(tailor.hasTag("shopkeeper"), "Tailor must carry the 'shopkeeper' tag");
        assertFalse(tailor.aggressive(), "Tailor should not be aggressive");
        assertEquals(9999, tailor.maxHp(), "Tailor should be unkillable-in-practice");
        assertEquals("muddy-hollow", tailor.spawnRoomId().getValue());
    }

    @Test
    void giantSpider_dropsSpiderSilk() throws RepositoryException {
        JsonMobTemplateRepository repo = new JsonMobTemplateRepository(Path.of("data"));

        MobTemplate spider = repo.findAll().stream()
            .filter(t -> "giant-spider".equals(t.id().getValue()))
            .findFirst()
            .orElse(null);

        assertNotNull(spider, "Giant spider template must be present");
        assertTrue(spider.lootTable().stream().anyMatch(l -> "spider-silk".equals(l.itemId().getValue())),
            "Giant spider must drop spider-silk");
    }

    @Test
    void tailoringRecipes_loadFromDedicatedSubdirectory() throws RecipeRepositoryException {
        JsonRecipeRepository repo = new JsonRecipeRepository(Path.of("data"), "recipes/tailoring");

        List<Recipe> recipes = repo.findAll();

        assertTrue(recipes.size() >= 5,
            "Expected at least five tailoring recipes, got " + recipes.size());
    }

    @Test
    void tailoringRecipes_haveAtLeastOneMinSkillGate() throws RecipeRepositoryException {
        JsonRecipeRepository repo = new JsonRecipeRepository(Path.of("data"), "recipes/tailoring");

        boolean gated = repo.findAll().stream().anyMatch(r -> r.minSkill() >= 4);

        assertTrue(gated, "At least one tailoring recipe must be gated behind min_skill 4");
    }

    @Test
    void tailoringRecipes_doNotLeakIntoBlacksmithSet() throws RecipeRepositoryException {
        JsonRecipeRepository blacksmithRepo = new JsonRecipeRepository(Path.of("data"));

        boolean leaked = blacksmithRepo.findAll().stream()
            .anyMatch(r -> r.id().value().startsWith("tailoring-"));

        assertFalse(leaked, "Tailoring recipes must not leak into the blacksmith recipe set");
    }

    @Test
    void graveLinen_isGatherableFromRealNode() throws Exception {
        JsonResourceNodeRepository repo = new JsonResourceNodeRepository(Path.of("data"));

        List<ResourceNode> nodes = repo.findAll();

        assertTrue(nodes.stream().anyMatch(n -> "grave-linen".equals(n.yieldItemId().getValue())),
            "A resource node must yield grave-linen");
    }

    @Test
    void silkweaveRobe_isChestArmorWithCasterStats() throws RepositoryException {
        JsonItemRepository itemRepository = new JsonItemRepository(Path.of("data"));

        Optional<Item> robeOpt = itemRepository.findById(ItemId.of("silkweave-robe"));

        assertTrue(robeOpt.isPresent(), "Silkweave Robe item must exist");
        Item robe = robeOpt.get();
        assertEquals(EquipmentSlot.CHEST, robe.getEquipSlot(),
            "Silkweave Robe must equip in the chest slot");
        assertTrue(robe.getAttributes().getStats().getOrDefault("intellect", 0) > 0,
            "Silkweave Robe must provide intellect for casters");
        assertTrue(robe.getAttributes().getStats().getOrDefault("mana", 0) > 0,
            "Silkweave Robe must provide mana for casters");
    }

    @Test
    void shroudweaveHood_isHeadArmorWithCasterStats() throws RepositoryException {
        JsonItemRepository itemRepository = new JsonItemRepository(Path.of("data"));

        Optional<Item> hoodOpt = itemRepository.findById(ItemId.of("shroudweave-hood"));

        assertTrue(hoodOpt.isPresent(), "Shroudweave Hood item must exist");
        Item hood = hoodOpt.get();
        assertEquals(EquipmentSlot.HEAD, hood.getEquipSlot(),
            "Shroudweave Hood must equip in the head slot");
        assertTrue(hood.getAttributes().getStats().getOrDefault("wisdom", 0) > 0,
            "Shroudweave Hood must provide wisdom for casters");
    }
}
