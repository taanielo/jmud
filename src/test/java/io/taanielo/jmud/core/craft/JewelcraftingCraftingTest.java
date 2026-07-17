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
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.ItemRepository;

/**
 * Unit tests for the jewelcrafting path of {@link CraftingService}: cutting rings and necklaces from
 * raw gems, driven by the {@link CrafterProfile#jeweler()} profile without any networking. Covers the
 * happy path, a missing-material failure, the {@code min_skill} gate and proficiency growth.
 */
class JewelcraftingCraftingTest {

    private static final ItemId ROUGH_QUARTZ = ItemId.of("rough-quartz");
    private static final ItemId RAW_GARNET = ItemId.of("raw-garnet");
    private static final ItemId QUARTZ_BAND = ItemId.of("quartz-band");
    private static final ItemId HEARTGEM = ItemId.of("starcut-heartgem");

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

    private final Recipe bandRecipe = new Recipe(
        RecipeId.of("jewelcrafting-quartz-band"), "Quartz Band", QUARTZ_BAND, 30,
        List.of(new RecipeMaterial(ROUGH_QUARTZ, 2)));

    private final Recipe heartgemRecipe = new Recipe(
        RecipeId.of("jewelcrafting-starcut-heartgem"), "Starcut Heartgem", HEARTGEM, 150,
        List.of(new RecipeMaterial(RAW_GARNET, 4), new RecipeMaterial(ROUGH_QUARTZ, 3)),
        4, Recipe.DEFAULT_PROFICIENCY_GAIN);

    private final CraftingService jewelcrafting =
        new CraftingService(List.of(bandRecipe, heartgemRecipe), itemRepository,
            CrafterProfile.jeweler());

    JewelcraftingCraftingTest() {
        catalogue.put(ROUGH_QUARTZ, item(ROUGH_QUARTZ, "Rough Quartz"));
        catalogue.put(RAW_GARNET, item(RAW_GARNET, "Raw Garnet"));
        catalogue.put(QUARTZ_BAND, item(QUARTZ_BAND, "Quartz Band"));
        catalogue.put(HEARTGEM, item(HEARTGEM, "Starcut Heartgem"));
    }

    private static Item item(ItemId id, String name) {
        return Item.builder(id, name, "desc.", ItemAttributes.empty()).weight(1).value(6).build();
    }

    private static Player player(int gold) {
        return Player.of(User.of(Username.of("jeweler"), Password.hash("pw", 1000)), "prompt").withGold(gold);
    }

    private Player withMaterial(Player player, ItemId id, String name, int count) {
        for (int i = 0; i < count; i++) {
            player = player.addItem(item(id, name));
        }
        return player;
    }

    @Test
    void cutSucceedsAndConsumesMaterialsAndGoldGrantingAccessory() {
        Player player = withMaterial(player(50), ROUGH_QUARTZ, "Rough Quartz", 2);

        CraftOutcome outcome = jewelcrafting.craft(player, "quartz band");

        assertTrue(outcome.success(), () -> outcome.message());
        Player updated = outcome.updatedPlayer();
        assertEquals(20, updated.getGold(), "gold cost of 30 deducted");
        assertEquals(0, updated.getInventory().stream().filter(i -> i.getId().equals(ROUGH_QUARTZ)).count(),
            "gems consumed");
        assertEquals(1, updated.getInventory().stream().filter(i -> i.getId().equals(QUARTZ_BAND)).count(),
            "cut accessory added");
        assertTrue(outcome.message().contains("jeweler"), () -> outcome.message());
    }

    @Test
    void cutFailsWhenMaterialsMissingAndConsumesNothing() {
        Player player = withMaterial(player(50), ROUGH_QUARTZ, "Rough Quartz", 1);

        CraftOutcome outcome = jewelcrafting.craft(player, "quartz band");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("Rough Quartz"), () -> outcome.message());
    }

    @Test
    void cutFailsWhenSkillTooLowForGatedRecipe() {
        Player player = withMaterial(
            withMaterial(player(200), RAW_GARNET, "Raw Garnet", 4),
            ROUGH_QUARTZ, "Rough Quartz", 3);

        CraftOutcome outcome = jewelcrafting.craft(player, "starcut heartgem");

        assertFalse(outcome.success(), () -> outcome.message());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("jewelcrafting level 4"), () -> outcome.message());
    }

    @Test
    void cutGrowsJewelcraftingProficiency() {
        Player player = withMaterial(player(50), ROUGH_QUARTZ, "Rough Quartz", 2);

        CraftOutcome outcome = jewelcrafting.craft(player, "quartz band");

        assertTrue(outcome.success(), () -> outcome.message());
        assertEquals(Recipe.DEFAULT_PROFICIENCY_GAIN,
            outcome.updatedPlayer().proficiencies().points(ProfessionId.JEWELCRAFTING),
            "a successful cut grows jewelcrafting proficiency");
    }

    @Test
    void bareCutListsRecipesWithJewelerWording() {
        Player player = withMaterial(player(50), ROUGH_QUARTZ, "Rough Quartz", 2);

        List<String> lines = jewelcrafting.formatRecipes(player);

        String body = String.join("\n", lines);
        assertTrue(body.contains("jeweler"), body);
        assertTrue(body.contains("CUT <item>"), body);
        assertTrue(body.contains("Quartz Band"), body);
        assertTrue(body.contains("have 2 / need 2"), body);
        assertTrue(body.contains("[requires jewelcrafting 4]"), body);
    }

    @Test
    void crafterProfileJewelerCarriesExpectedWording() {
        CrafterProfile jeweler = CrafterProfile.jeweler();
        assertEquals("jeweler", jeweler.crafter());
        assertEquals("CUT", jeweler.command());
        assertEquals("Cut", jeweler.capitalizedVerb());
        assertEquals(ProfessionId.JEWELCRAFTING, jeweler.profession());
    }
}
