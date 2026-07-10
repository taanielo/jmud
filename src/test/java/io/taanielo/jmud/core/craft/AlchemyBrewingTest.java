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
 * Unit tests for the alchemy path of {@link CraftingService}: brewing potions from gathered herbs,
 * driven by the {@link CrafterProfile#alchemist()} profile without any networking.
 */
class AlchemyBrewingTest {

    private static final ItemId WILD_HERBS = ItemId.of("wild-herbs");
    private static final ItemId HEALTH_POTION = ItemId.of("health-potion");

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

    private final Recipe healthRecipe = new Recipe(
        RecipeId.of("alchemy-health-potion"), "Health Potion", HEALTH_POTION, 8,
        List.of(new RecipeMaterial(WILD_HERBS, 2)));

    private final CraftingService alchemy =
        new CraftingService(List.of(healthRecipe), itemRepository, CrafterProfile.alchemist());

    AlchemyBrewingTest() {
        catalogue.put(WILD_HERBS, item(WILD_HERBS, "Wild Herbs"));
        catalogue.put(HEALTH_POTION, item(HEALTH_POTION, "Health Potion"));
    }

    private static Item item(ItemId id, String name) {
        return Item.builder(id, name, "desc.", ItemAttributes.empty()).weight(1).value(5).build();
    }

    private static Player player(int gold) {
        return Player.of(User.of(Username.of("brewer"), Password.hash("pw", 1000)), "prompt").withGold(gold);
    }

    private Player withHerbs(Player player, int count) {
        for (int i = 0; i < count; i++) {
            player = player.addItem(item(WILD_HERBS, "Wild Herbs"));
        }
        return player;
    }

    @Test
    void brewSucceedsAndConsumesHerbsAndGoldGrantingPotion() {
        Player player = withHerbs(player(20), 2);

        CraftOutcome outcome = alchemy.craft(player, "health potion");

        assertTrue(outcome.success(), () -> outcome.message());
        Player updated = outcome.updatedPlayer();
        assertEquals(12, updated.getGold(), "gold cost of 8 deducted");
        assertEquals(0, updated.getInventory().stream().filter(i -> i.getId().equals(WILD_HERBS)).count(),
            "both herbs consumed");
        assertEquals(1, updated.getInventory().stream().filter(i -> i.getId().equals(HEALTH_POTION)).count(),
            "brewed potion added");
        assertTrue(outcome.message().contains("alchemist"), () -> outcome.message());
    }

    @Test
    void brewFailsWhenHerbsMissingAndConsumesNothing() {
        Player player = withHerbs(player(20), 1);

        CraftOutcome outcome = alchemy.craft(player, "health potion");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("Wild Herbs"), () -> outcome.message());
        assertTrue(outcome.message().contains("brew"), () -> outcome.message());
    }

    @Test
    void brewFailsWhenGoldMissingAndConsumesNothing() {
        Player player = withHerbs(player(2), 2);

        CraftOutcome outcome = alchemy.craft(player, "health potion");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("gold"), () -> outcome.message());
    }

    @Test
    void bareBrewListsRecipesWithAlchemistWording() {
        List<String> lines = alchemy.formatRecipes(withHerbs(player(20), 1));

        String body = String.join("\n", lines);
        assertTrue(body.contains("alchemist"), body);
        assertTrue(body.contains("BREW <item>"), body);
        assertTrue(body.contains("Health Potion"), body);
        assertTrue(body.contains("have 1 / need 2"), body);
        assertTrue(body.contains("8 gold"), body);
    }

    @Test
    void blankInputPromptsWithBrewVerb() {
        CraftOutcome outcome = alchemy.craft(player(20), "");

        assertFalse(outcome.success());
        assertTrue(outcome.message().startsWith("Brew what?"), () -> outcome.message());
    }

    @Test
    void crafterProfileFactoriesCarryExpectedWording() {
        CrafterProfile blacksmith = CrafterProfile.blacksmith();
        assertEquals("blacksmith", blacksmith.crafter());
        assertEquals("CRAFT", blacksmith.command());
        assertEquals("Craft", blacksmith.capitalizedVerb());

        CrafterProfile alchemist = CrafterProfile.alchemist();
        assertEquals("alchemist", alchemist.crafter());
        assertEquals("BREW", alchemist.command());
        assertEquals("Brew", alchemist.capitalizedVerb());
    }
}
