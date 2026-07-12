package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.craft.Recipe;
import io.taanielo.jmud.core.craft.RecipeRepositoryException;
import io.taanielo.jmud.core.craft.repository.json.JsonRecipeRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.json.JsonItemRepository;

/**
 * Content smoke-test for the neck/finger jewelry slots (issue #502): confirms the shipped amulet and
 * ring items load through the versioned item schema into the new {@link EquipmentSlot#NECK} and
 * {@link EquipmentSlot#FINGER} slots, and that the crafted signet closes its blacksmith recipe loop.
 */
class JewelryContentTest {

    private static final ItemId COPPER_RING = ItemId.of("copper-ring");
    private static final ItemId TRAVELERS_AMULET = ItemId.of("travelers-amulet");
    private static final ItemId SIGNET_OF_WARDING = ItemId.of("signet-of-warding");

    @Test
    void copperRingLoadsAsCommonFingerGear() throws RepositoryException {
        Item ring = new JsonItemRepository(Path.of("data")).findById(COPPER_RING).orElse(null);

        assertNotNull(ring, "Copper Ring item must load");
        assertEquals(EquipmentSlot.FINGER, ring.getEquipSlot());
        assertEquals(1, ring.getAttributes().getStats().getOrDefault("ac", 0));
    }

    @Test
    void travelersAmuletLoadsAsNeckGear() throws RepositoryException {
        Item amulet = new JsonItemRepository(Path.of("data")).findById(TRAVELERS_AMULET).orElse(null);

        assertNotNull(amulet, "Traveler's Amulet item must load");
        assertEquals(EquipmentSlot.NECK, amulet.getEquipSlot());
        assertEquals(1, amulet.getAttributes().getStats().getOrDefault("dexterity", 0));
    }

    @Test
    void signetOfWardingLoadsAsUncommonFingerGear() throws RepositoryException {
        Item signet = new JsonItemRepository(Path.of("data")).findById(SIGNET_OF_WARDING).orElse(null);

        assertNotNull(signet, "Signet of Warding item must load");
        assertEquals(EquipmentSlot.FINGER, signet.getEquipSlot());
        assertEquals(Rarity.UNCOMMON, signet.getRarity());
        assertEquals(2, signet.getAttributes().getStats().getOrDefault("ac", 0));
    }

    @Test
    void signetOfWardingIsCraftableViaBlacksmithRecipe() throws RecipeRepositoryException {
        Recipe recipe = new JsonRecipeRepository(Path.of("data")).findAll().stream()
            .filter(r -> r.id().value().equals("signet-of-warding"))
            .findFirst()
            .orElse(null);

        assertNotNull(recipe, "Signet of Warding recipe must load from the blacksmith recipe set");
        assertEquals(SIGNET_OF_WARDING, recipe.outputItemId());
        assertTrue(recipe.goldCost() > 0, "Recipe should carry a meaningful gold cost");
    }
}
