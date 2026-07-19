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
 * Unit tests for the leatherworking path of {@link CraftingService}: tanning light armor from beast
 * pelts and fangs, driven by the {@link CrafterProfile#leatherworker()} profile without any
 * networking. Covers the happy path, a missing-material failure, and the {@code min_skill} gate.
 */
class LeatherworkingCraftingTest {

    private static final ItemId WOLF_PELT = ItemId.of("wolf-pelt");
    private static final ItemId DIRE_WOLF_FANG = ItemId.of("dire-wolf-fang");
    private static final ItemId TROLL_TOOTH = ItemId.of("troll-tooth");
    private static final ItemId LEATHER_VEST = ItemId.of("boiled-leather-vest");
    private static final ItemId DIREHIDE_COWL = ItemId.of("direhide-cowl");

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

    private final Recipe vestRecipe = new Recipe(
        RecipeId.of("leatherworking-boiled-leather-vest"), "Boiled Leather Vest", LEATHER_VEST, 35,
        List.of(new RecipeMaterial(WOLF_PELT, 3)));

    private final Recipe cowlRecipe = new Recipe(
        RecipeId.of("leatherworking-direhide-cowl"), "Direhide Cowl", DIREHIDE_COWL, 90,
        List.of(new RecipeMaterial(WOLF_PELT, 2), new RecipeMaterial(DIRE_WOLF_FANG, 2),
            new RecipeMaterial(TROLL_TOOTH, 1)),
        4, Recipe.DEFAULT_PROFICIENCY_GAIN);

    private final CraftingService leatherworking =
        new CraftingService(List.of(vestRecipe, cowlRecipe), itemRepository,
            CrafterProfile.leatherworker());

    LeatherworkingCraftingTest() {
        catalogue.put(WOLF_PELT, item(WOLF_PELT, "Wolf Pelt"));
        catalogue.put(DIRE_WOLF_FANG, item(DIRE_WOLF_FANG, "Dire Wolf Fang"));
        catalogue.put(TROLL_TOOTH, item(TROLL_TOOTH, "Troll Tooth"));
        catalogue.put(LEATHER_VEST, item(LEATHER_VEST, "Boiled Leather Vest"));
        catalogue.put(DIREHIDE_COWL, item(DIREHIDE_COWL, "Direhide Cowl"));
    }

    private static Item item(ItemId id, String name) {
        return Item.builder(id, name, "desc.", ItemAttributes.empty()).weight(1).value(5).build();
    }

    private static Player player(int gold) {
        return Player.of(User.of(Username.of("tanner"), Password.hash("pw", 1000)), "prompt").withGold(gold);
    }

    private Player withMaterial(Player player, ItemId id, String name, int count) {
        for (int i = 0; i < count; i++) {
            player = player.addItem(item(id, name));
        }
        return player;
    }

    @Test
    void tanSucceedsAndConsumesMaterialsAndGoldGrantingArmor() {
        Player player = withMaterial(player(50), WOLF_PELT, "Wolf Pelt", 3);

        CraftOutcome outcome = leatherworking.craft(player, "boiled leather vest");

        assertTrue(outcome.success(), () -> outcome.message());
        Player updated = outcome.updatedPlayer();
        assertEquals(15, updated.getGold(), "gold cost of 35 deducted");
        assertEquals(0, updated.getInventory().stream().filter(i -> i.getId().equals(WOLF_PELT)).count(),
            "pelts consumed");
        assertEquals(1, updated.getInventory().stream().filter(i -> i.getId().equals(LEATHER_VEST)).count(),
            "tanned armor added");
        assertTrue(outcome.message().contains("leatherworker"), () -> outcome.message());
    }

    @Test
    void tanFailsWhenMaterialsMissingAndConsumesNothing() {
        Player player = withMaterial(player(50), WOLF_PELT, "Wolf Pelt", 1);

        CraftOutcome outcome = leatherworking.craft(player, "boiled leather vest");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("Wolf Pelt"), () -> outcome.message());
    }

    @Test
    void tanFailsWhenSkillTooLowForGatedRecipe() {
        Player player = withMaterial(
            withMaterial(withMaterial(player(100), WOLF_PELT, "Wolf Pelt", 2),
                DIRE_WOLF_FANG, "Dire Wolf Fang", 2),
            TROLL_TOOTH, "Troll Tooth", 1);

        CraftOutcome outcome = leatherworking.craft(player, "direhide cowl");

        assertFalse(outcome.success(), () -> outcome.message());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("leatherworking level 4"), () -> outcome.message());
    }

    @Test
    void tanGrowsLeatherworkingProficiency() {
        Player player = withMaterial(player(50), WOLF_PELT, "Wolf Pelt", 3);

        CraftOutcome outcome = leatherworking.craft(player, "boiled leather vest");

        assertTrue(outcome.success(), () -> outcome.message());
        assertEquals(Recipe.DEFAULT_PROFICIENCY_GAIN,
            outcome.updatedPlayer().proficiencies().points(ProfessionId.LEATHERWORKING),
            "a successful tan grows leatherworking proficiency");
    }

    @Test
    void bareTanListsRecipesWithLeatherworkerWording() {
        Player player = withMaterial(player(50), WOLF_PELT, "Wolf Pelt", 2);

        List<String> lines = leatherworking.formatRecipes(player);

        String body = String.join("\n", lines);
        assertTrue(body.contains("leatherworker"), body);
        assertTrue(body.contains("TAN <item>"), body);
        assertTrue(body.contains("Boiled Leather Vest"), body);
        assertTrue(body.contains("have 2 / need 3"), body);
        assertTrue(body.contains("[requires leatherworking 4]"), body);
    }

    @Test
    void crafterProfileLeatherworkerCarriesExpectedWording() {
        CrafterProfile leatherworker = CrafterProfile.leatherworker();
        assertEquals("leatherworker", leatherworker.crafter());
        assertEquals("TAN", leatherworker.command());
        assertEquals("Tan", leatherworker.capitalizedVerb());
        assertEquals(ProfessionId.LEATHERWORKING, leatherworker.profession());
    }
}
