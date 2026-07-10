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
 * Unit tests for the cooking path of {@link CraftingService}: cooking meals from raw ingredients,
 * driven by the {@link CrafterProfile#cook()} profile without any networking.
 */
class CookingCraftingTest {

    private static final ItemId MEAT = ItemId.of("meat");
    private static final ItemId WILD_HERBS = ItemId.of("wild-herbs");
    private static final ItemId HEARTY_STEW = ItemId.of("hearty-stew");

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

    private final Recipe stewRecipe = new Recipe(
        RecipeId.of("cooking-hearty-stew"), "Hearty Stew", HEARTY_STEW, 10,
        List.of(new RecipeMaterial(MEAT, 1), new RecipeMaterial(WILD_HERBS, 2)));

    private final CraftingService cooking =
        new CraftingService(List.of(stewRecipe), itemRepository, CrafterProfile.cook());

    CookingCraftingTest() {
        catalogue.put(MEAT, item(MEAT, "Slab of Meat"));
        catalogue.put(WILD_HERBS, item(WILD_HERBS, "Wild Herbs"));
        catalogue.put(HEARTY_STEW, item(HEARTY_STEW, "Hearty Stew"));
    }

    private static Item item(ItemId id, String name) {
        return Item.builder(id, name, "desc.", ItemAttributes.empty()).weight(1).value(5).build();
    }

    private static Player player(int gold) {
        return Player.of(User.of(Username.of("cooker"), Password.hash("pw", 1000)), "prompt").withGold(gold);
    }

    private Player withIngredient(Player player, ItemId id, String name, int count) {
        for (int i = 0; i < count; i++) {
            player = player.addItem(item(id, name));
        }
        return player;
    }

    @Test
    void cookSucceedsAndConsumesIngredientsAndGoldGrantingMeal() {
        Player player = withIngredient(withIngredient(player(20), MEAT, "Slab of Meat", 1),
            WILD_HERBS, "Wild Herbs", 2);

        CraftOutcome outcome = cooking.craft(player, "hearty stew");

        assertTrue(outcome.success(), () -> outcome.message());
        Player updated = outcome.updatedPlayer();
        assertEquals(10, updated.getGold(), "gold cost of 10 deducted");
        assertEquals(0, updated.getInventory().stream().filter(i -> i.getId().equals(MEAT)).count(),
            "meat consumed");
        assertEquals(0, updated.getInventory().stream().filter(i -> i.getId().equals(WILD_HERBS)).count(),
            "herbs consumed");
        assertEquals(1, updated.getInventory().stream().filter(i -> i.getId().equals(HEARTY_STEW)).count(),
            "cooked meal added");
        assertTrue(outcome.message().contains("cook"), () -> outcome.message());
    }

    @Test
    void cookFailsWhenNoCookPresentIsHandledByCommandNotService() {
        // The service itself has no notion of a cook being present; presence is enforced by the
        // command layer. This test documents that a valid recipe with materials still succeeds here.
        Player player = withIngredient(withIngredient(player(20), MEAT, "Slab of Meat", 1),
            WILD_HERBS, "Wild Herbs", 2);

        assertTrue(cooking.craft(player, "hearty stew").success());
    }

    @Test
    void cookFailsWhenIngredientsMissingAndConsumesNothing() {
        Player player = withIngredient(player(20), MEAT, "Slab of Meat", 1);

        CraftOutcome outcome = cooking.craft(player, "hearty stew");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("Wild Herbs"), () -> outcome.message());
        assertTrue(outcome.message().contains("cook"), () -> outcome.message());
    }

    @Test
    void cookFailsWhenGoldMissingAndConsumesNothing() {
        Player player = withIngredient(withIngredient(player(2), MEAT, "Slab of Meat", 1),
            WILD_HERBS, "Wild Herbs", 2);

        CraftOutcome outcome = cooking.craft(player, "hearty stew");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("gold"), () -> outcome.message());
    }

    @Test
    void bareCookListsRecipesWithCookWording() {
        Player player = withIngredient(player(20), MEAT, "Slab of Meat", 1);

        List<String> lines = cooking.formatRecipes(player);

        String body = String.join("\n", lines);
        assertTrue(body.contains("cook"), body);
        assertTrue(body.contains("COOK <item>"), body);
        assertTrue(body.contains("Hearty Stew"), body);
        assertTrue(body.contains("have 1 / need 1"), body);
        assertTrue(body.contains("have 0 / need 2"), body);
        assertTrue(body.contains("10 gold"), body);
    }

    @Test
    void blankInputPromptsWithCookVerb() {
        CraftOutcome outcome = cooking.craft(player(20), "");

        assertFalse(outcome.success());
        assertTrue(outcome.message().startsWith("Cook what?"), () -> outcome.message());
    }

    @Test
    void crafterProfileCookCarriesExpectedWording() {
        CrafterProfile cook = CrafterProfile.cook();
        assertEquals("cook", cook.crafter());
        assertEquals("COOK", cook.command());
        assertEquals("Cook", cook.capitalizedVerb());
    }
}
