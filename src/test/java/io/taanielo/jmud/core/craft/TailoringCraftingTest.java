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
 * Unit tests for the tailoring path of {@link CraftingService}: sewing cloth caster armor from spider
 * silk and grave linen, driven by the {@link CrafterProfile#tailor()} profile without any networking.
 * Covers the happy path, a missing-material failure, the {@code min_skill} gate and proficiency growth.
 */
class TailoringCraftingTest {

    private static final ItemId SPIDER_SILK = ItemId.of("spider-silk");
    private static final ItemId GRAVE_LINEN = ItemId.of("grave-linen");
    private static final ItemId GLOVES = ItemId.of("silkweave-gloves");
    private static final ItemId HOOD = ItemId.of("shroudweave-hood");

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

    private final Recipe glovesRecipe = new Recipe(
        RecipeId.of("tailoring-silkweave-gloves"), "Silkweave Gloves", GLOVES, 25,
        List.of(new RecipeMaterial(SPIDER_SILK, 2)));

    private final Recipe hoodRecipe = new Recipe(
        RecipeId.of("tailoring-shroudweave-hood"), "Shroudweave Hood", HOOD, 90,
        List.of(new RecipeMaterial(GRAVE_LINEN, 3), new RecipeMaterial(SPIDER_SILK, 3)),
        4, Recipe.DEFAULT_PROFICIENCY_GAIN);

    private final CraftingService tailoring =
        new CraftingService(List.of(glovesRecipe, hoodRecipe), itemRepository,
            CrafterProfile.tailor());

    TailoringCraftingTest() {
        catalogue.put(SPIDER_SILK, item(SPIDER_SILK, "Spider Silk"));
        catalogue.put(GRAVE_LINEN, item(GRAVE_LINEN, "Grave Linen"));
        catalogue.put(GLOVES, item(GLOVES, "Silkweave Gloves"));
        catalogue.put(HOOD, item(HOOD, "Shroudweave Hood"));
    }

    private static Item item(ItemId id, String name) {
        return Item.builder(id, name, "desc.", ItemAttributes.empty()).weight(1).value(6).build();
    }

    private static Player player(int gold) {
        return Player.of(User.of(Username.of("tailor"), Password.hash("pw", 1000)), "prompt").withGold(gold);
    }

    private Player withMaterial(Player player, ItemId id, String name, int count) {
        for (int i = 0; i < count; i++) {
            player = player.addItem(item(id, name));
        }
        return player;
    }

    @Test
    void sewSucceedsAndConsumesMaterialsAndGoldGrantingArmor() {
        Player player = withMaterial(player(50), SPIDER_SILK, "Spider Silk", 2);

        CraftOutcome outcome = tailoring.craft(player, "silkweave gloves");

        assertTrue(outcome.success(), () -> outcome.message());
        Player updated = outcome.updatedPlayer();
        assertEquals(25, updated.getGold(), "gold cost of 25 deducted");
        assertEquals(0, updated.getInventory().stream().filter(i -> i.getId().equals(SPIDER_SILK)).count(),
            "cloth consumed");
        assertEquals(1, updated.getInventory().stream().filter(i -> i.getId().equals(GLOVES)).count(),
            "sewn armor added");
        assertTrue(outcome.message().contains("tailor"), () -> outcome.message());
    }

    @Test
    void sewFailsWhenMaterialsMissingAndConsumesNothing() {
        Player player = withMaterial(player(50), SPIDER_SILK, "Spider Silk", 1);

        CraftOutcome outcome = tailoring.craft(player, "silkweave gloves");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("Spider Silk"), () -> outcome.message());
    }

    @Test
    void sewFailsWhenSkillTooLowForGatedRecipe() {
        Player player = withMaterial(
            withMaterial(player(200), GRAVE_LINEN, "Grave Linen", 3),
            SPIDER_SILK, "Spider Silk", 3);

        CraftOutcome outcome = tailoring.craft(player, "shroudweave hood");

        assertFalse(outcome.success(), () -> outcome.message());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("tailoring level 4"), () -> outcome.message());
    }

    @Test
    void sewGrowsTailoringProficiency() {
        Player player = withMaterial(player(50), SPIDER_SILK, "Spider Silk", 2);

        CraftOutcome outcome = tailoring.craft(player, "silkweave gloves");

        assertTrue(outcome.success(), () -> outcome.message());
        assertEquals(Recipe.DEFAULT_PROFICIENCY_GAIN,
            outcome.updatedPlayer().proficiencies().points(ProfessionId.TAILORING),
            "a successful sew grows tailoring proficiency");
    }

    @Test
    void bareSewListsRecipesWithTailorWording() {
        Player player = withMaterial(player(50), SPIDER_SILK, "Spider Silk", 2);

        List<String> lines = tailoring.formatRecipes(player);

        String body = String.join("\n", lines);
        assertTrue(body.contains("tailor"), body);
        assertTrue(body.contains("SEW <item>"), body);
        assertTrue(body.contains("Silkweave Gloves"), body);
        assertTrue(body.contains("have 2 / need 2"), body);
        assertTrue(body.contains("[requires tailoring 4]"), body);
    }

    @Test
    void crafterProfileTailorCarriesExpectedWording() {
        CrafterProfile tailor = CrafterProfile.tailor();
        assertEquals("tailor", tailor.crafter());
        assertEquals("SEW", tailor.command());
        assertEquals("Sew", tailor.capitalizedVerb());
        assertEquals(ProfessionId.TAILORING, tailor.profession());
    }
}
