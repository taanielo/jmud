package io.taanielo.jmud.core.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.ItemRepository;

/**
 * Unit tests for {@link CraftingService}, exercising crafting without any networking.
 */
class CraftingServiceTest {

    private static final ItemId WOLF_PELT = ItemId.of("wolf-pelt");
    private static final ItemId CLOAK = ItemId.of("wolf-pelt-cloak");

    private final Map<ItemId, Item> catalogue = new HashMap<>();
    private final ItemRepository itemRepository = new ItemRepository() {
        @Override
        public void save(Item item) {
            catalogue.put(item.getId(), item);
        }

        @Override
        public Optional<Item> findById(ItemId id) {
            return Optional.ofNullable(catalogue.get(id));
        }
    };

    private final Recipe cloakRecipe = new Recipe(
        RecipeId.of("wolf-pelt-cloak"), "Wolf Pelt Cloak", CLOAK, 15,
        List.of(new RecipeMaterial(WOLF_PELT, 2)));

    private final CraftingService service = new CraftingService(List.of(cloakRecipe), itemRepository);

    CraftingServiceTest() {
        catalogue.put(WOLF_PELT, item(WOLF_PELT, "Wolf Pelt", null));
        catalogue.put(CLOAK, cloakItem());
    }

    private static Item item(ItemId id, String name, EquipmentSlot slot) {
        var builder = Item.builder(id, name, "desc.", ItemAttributes.empty()).weight(1).value(5);
        if (slot != null) {
            builder.equipSlot(slot);
        }
        return builder.build();
    }

    private static Item cloakItem() {
        return Item.builder(CLOAK, "Wolf Pelt Cloak", "A cloak.", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.CHEST)
            .weight(3)
            .value(40)
            .build();
    }

    private static Player player(int gold) {
        return Player.of(User.of(Username.of("hero"), Password.hash("pw", 1000)), "prompt").withGold(gold);
    }

    private Player withPelts(Player player, int count) {
        for (int i = 0; i < count; i++) {
            player = player.addItem(item(WOLF_PELT, "Wolf Pelt", null));
        }
        return player;
    }

    @Test
    void craftSucceedsWhenMaterialsAndGoldPresent() {
        Player player = withPelts(player(50), 2);

        CraftOutcome outcome = service.craft(player, "wolf pelt cloak");

        assertTrue(outcome.success());
        Player updated = outcome.updatedPlayer();
        assertEquals(35, updated.getGold(), "gold cost of 15 deducted");
        assertEquals(0, updated.getInventory().stream().filter(i -> i.getId().equals(WOLF_PELT)).count(),
            "both pelts consumed");
        assertEquals(1, updated.getInventory().stream().filter(i -> i.getId().equals(CLOAK)).count(),
            "crafted cloak added");
    }

    @Test
    void craftConsumesOnlyRequiredQuantityOfMaterials() {
        Player player = withPelts(player(50), 3);

        CraftOutcome outcome = service.craft(player, "wolf-pelt-cloak");

        assertTrue(outcome.success());
        assertEquals(1, outcome.updatedPlayer().getInventory().stream()
            .filter(i -> i.getId().equals(WOLF_PELT)).count(), "one spare pelt kept");
    }

    @Test
    void craftFailsWhenMaterialMissingAndConsumesNothing() {
        Player player = withPelts(player(50), 1);

        CraftOutcome outcome = service.craft(player, "wolf pelt cloak");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("Wolf Pelt"), () -> outcome.message());
        assertTrue(outcome.message().contains("1 more"), () -> outcome.message());
    }

    @Test
    void craftFailsWhenGoldMissingAndConsumesNothing() {
        Player player = withPelts(player(5), 2);

        CraftOutcome outcome = service.craft(player, "wolf pelt cloak");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("gold"), () -> outcome.message());
    }

    @Test
    void craftFailsForUnknownRecipe() {
        CraftOutcome outcome = service.craft(withPelts(player(50), 2), "dragon plate");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("no recipe"), () -> outcome.message());
    }

    @Test
    void craftWithBlankInputPromptsForRecipe() {
        CraftOutcome outcome = service.craft(player(50), "");

        assertFalse(outcome.success());
        assertTrue(outcome.message().startsWith("Craft what?"), () -> outcome.message());
    }

    @Test
    void formatRecipesShowsLiveHaveNeedCountsAndGoldCost() {
        Player player = withPelts(player(50), 1);

        List<String> lines = service.formatRecipes(player);

        String body = String.join("\n", lines);
        assertTrue(body.contains("Wolf Pelt Cloak"), body);
        assertTrue(body.contains("have 1 / need 2"), body);
        assertTrue(body.contains("15 gold"), body);
    }

    @Test
    void formatRecipesReportsEmptyTableWhenNoRecipes() {
        CraftingService empty = new CraftingService(List.of(), itemRepository);

        List<String> lines = empty.formatRecipes(player(0));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("no recipes"), lines.get(0));
    }

    @Test
    void noCraftingServiceGatingIsHandledByEmptyRecipeTable() {
        // The "no blacksmith present" gate lives in the command context; here we assert the service
        // itself degrades gracefully when it simply has nothing to craft.
        CraftingService empty = new CraftingService(List.of(), itemRepository);

        CraftOutcome outcome = empty.craft(withPelts(player(50), 2), "wolf pelt cloak");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
    }

    @Test
    void successfulCraftGrantsProficiencyPointsInTheProfession() {
        Player player = withPelts(player(50), 2);

        CraftOutcome outcome = service.craft(player, "wolf pelt cloak");

        assertTrue(outcome.success());
        assertEquals(Recipe.DEFAULT_PROFICIENCY_GAIN,
            outcome.updatedPlayer().proficiencies().points(ProfessionId.BLACKSMITHING),
            "default proficiency gain awarded");
        assertEquals(0, outcome.updatedPlayer().proficiencies().level(ProfessionId.BLACKSMITHING),
            "25 points is not yet a full level");
    }

    @Test
    void crossingPointThresholdLevelsUpWithMessage() {
        Recipe fastRecipe = new Recipe(
            RecipeId.of("wolf-pelt-cloak"), "Wolf Pelt Cloak", CLOAK, 0,
            List.of(new RecipeMaterial(WOLF_PELT, 2)), 0, PlayerProficiencies.POINTS_PER_LEVEL);
        CraftingService fast = new CraftingService(List.of(fastRecipe), itemRepository);
        Player player = withPelts(player(0), 2);

        CraftOutcome outcome = fast.craft(player, "wolf pelt cloak");

        assertTrue(outcome.success());
        assertEquals(1, outcome.updatedPlayer().proficiencies().level(ProfessionId.BLACKSMITHING),
            "100 points is exactly one level");
        assertTrue(outcome.message().contains("proficiency rises to level 1"), () -> outcome.message());
    }

    @Test
    void lockedRecipeFailsWithoutConsumingMaterialsOrGold() {
        Recipe lockedRecipe = new Recipe(
            RecipeId.of("wolf-pelt-cloak"), "Wolf Pelt Cloak", CLOAK, 15,
            List.of(new RecipeMaterial(WOLF_PELT, 2)), 3, 25);
        CraftingService gated = new CraftingService(List.of(lockedRecipe), itemRepository);
        Player player = withPelts(player(50), 2);

        CraftOutcome outcome = gated.craft(player, "wolf pelt cloak");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer(), "no player change, so nothing consumed");
        assertTrue(outcome.message().contains("level 3"), () -> outcome.message());
        assertTrue(outcome.message().contains("level 0"), () -> outcome.message());
    }

    @Test
    void recipeWithoutProficiencyDataIsCraftableByAnyoneWithDefaults() {
        Recipe legacyRecipe = new Recipe(
            RecipeId.of("wolf-pelt-cloak"), "Wolf Pelt Cloak", CLOAK, 15,
            List.of(new RecipeMaterial(WOLF_PELT, 2)));
        assertEquals(0, legacyRecipe.minSkill(), "omitted min_skill defaults to no requirement");
        assertEquals(Recipe.DEFAULT_PROFICIENCY_GAIN, legacyRecipe.proficiencyGain());

        // Player.of() starts with no proficiency data, mirroring an existing save file.
        CraftOutcome outcome = new CraftingService(List.of(legacyRecipe), itemRepository)
            .craft(withPelts(player(50), 2), "wolf pelt cloak");

        assertTrue(outcome.success(), () -> outcome.message());
    }

    @Test
    void formatRecipesShowsProficiencyLevelAndFlagsLockedRecipes() {
        Recipe lockedRecipe = new Recipe(
            RecipeId.of("wolf-pelt-cloak"), "Wolf Pelt Cloak", CLOAK, 15,
            List.of(new RecipeMaterial(WOLF_PELT, 2)), 3, 25);
        CraftingService gated = new CraftingService(List.of(lockedRecipe), itemRepository);

        String body = String.join("\n", gated.formatRecipes(withPelts(player(50), 2)));

        assertTrue(body.contains("blacksmithing level: 0"), body);
        assertTrue(body.contains("[requires blacksmithing 3]"), body);
    }
}
