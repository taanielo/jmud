package io.taanielo.jmud.core.enchant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.craft.RecipeMaterial;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.AffixId;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAffix;
import io.taanielo.jmud.core.world.ItemAffixService;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Rarity;
import io.taanielo.jmud.core.world.RarityProfile;
import io.taanielo.jmud.core.world.repository.AffixRepository;
import io.taanielo.jmud.core.world.repository.ItemRepository;

/**
 * Unit tests for {@link EnchantingService}, exercising enchanting without any networking (AGENTS.md
 * §10): successful enchant, insufficient materials, max-affix rejection and non-equippable rejection.
 */
class EnchantingServiceTest {

    private static final ItemId ARCANE_DUST = ItemId.of("arcane-dust");
    private static final ItemId SWORD = ItemId.of("iron-sword");
    private static final ItemId POTION = ItemId.of("health-potion");

    private static final ItemAffix BEAR = new ItemAffix(
        AffixId.of("of-the-bear"), "of the Bear", Map.of("strength", 2), Set.of(Rarity.UNCOMMON, Rarity.RARE));

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

    private final AffixRepository affixRepository = new AffixRepository() {
        @Override
        public Optional<ItemAffix> findById(AffixId id) {
            return BEAR.id().equals(id) ? Optional.of(BEAR) : Optional.empty();
        }

        @Override
        public List<ItemAffix> findAll() {
            return List.of(BEAR);
        }
    };

    private final ItemAffixService itemAffixService = new ItemAffixService(affixRepository);

    private final EnchantRecipe bearRecipe = new EnchantRecipe(
        "enchant-of-the-bear", BEAR.id(), 40, List.of(new RecipeMaterial(ARCANE_DUST, 2)));

    private final EnchantingService service = new EnchantingService(
        List.of(bearRecipe), itemRepository, affixRepository, itemAffixService);

    EnchantingServiceTest() {
        catalogue.put(ARCANE_DUST, dust());
    }

    private static Item dust() {
        return Item.builder(ARCANE_DUST, "Arcane Dust", "Glowing residue.", ItemAttributes.empty())
            .weight(1).value(12).build();
    }

    private static Item sword(List<AffixId> affixes) {
        return Item.builder(SWORD, "Iron Sword", "A plain blade.", new ItemAttributes(Map.of("attack", 3)))
            .equipSlot(EquipmentSlot.WEAPON)
            .weight(4)
            .value(30)
            .rarity(RarityProfile.of(Rarity.UNCOMMON, affixes))
            .build();
    }

    private static Item potion() {
        return Item.builder(POTION, "Health Potion", "Restores health.", ItemAttributes.empty())
            .weight(1).value(8).build();
    }

    private static Player player(int gold) {
        return Player.of(User.of(Username.of("hero"), Password.hash("pw", 1000)), "prompt").withGold(gold);
    }

    private Player withDust(Player player, int count) {
        for (int i = 0; i < count; i++) {
            player = player.addItem(dust());
        }
        return player;
    }

    @Test
    void enchantSucceedsAddingAffixAndConsumingResources() {
        Player player = withDust(player(100), 2).addItem(sword(List.of()));

        EnchantOutcome outcome = service.enchant(player, "iron sword of the bear");

        assertTrue(outcome.success(), outcome.message());
        Player updated = outcome.updatedPlayer();
        assertNotNull(updated);
        assertEquals(60, updated.getGold(), "40 gold consumed");
        assertEquals(0, updated.getInventory().stream().filter(i -> i.getId().equals(ARCANE_DUST)).count(),
            "both dust consumed");
        Item enchantedSword = updated.getInventory().stream()
            .filter(i -> i.getId().equals(SWORD)).findFirst().orElseThrow();
        assertEquals(List.of(BEAR.id()), enchantedSword.getAffixes(), "affix attached to instance");
        assertTrue(outcome.message().contains("of the Bear"), outcome.message());
        assertTrue(outcome.message().contains("strength +2"), outcome.message());
    }

    @Test
    void enchantMatchesAffixByIdToken() {
        Player player = withDust(player(100), 2).addItem(sword(List.of()));

        EnchantOutcome outcome = service.enchant(player, "iron sword of-the-bear");

        assertTrue(outcome.success(), outcome.message());
    }

    @Test
    void enchantFailsWhenMaterialsInsufficientAndConsumesNothing() {
        Player player = withDust(player(100), 1).addItem(sword(List.of()));

        EnchantOutcome outcome = service.enchant(player, "iron sword of the bear");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("1 more Arcane Dust"), outcome.message());
    }

    @Test
    void enchantFailsWhenGoldInsufficientAndConsumesNothing() {
        Player player = withDust(player(5), 2).addItem(sword(List.of()));

        EnchantOutcome outcome = service.enchant(player, "iron sword of the bear");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("gold"), outcome.message());
    }

    @Test
    void enchantRejectsItemAlreadyAtAffixLimit() {
        Player player = withDust(player(100), 2).addItem(sword(List.of(BEAR.id())));

        EnchantOutcome outcome = service.enchant(player, "iron sword of the bear");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("already bears a rune of power"), outcome.message());
    }

    @Test
    void enchantRejectsNonEquippableItem() {
        Player player = withDust(player(100), 2).addItem(potion());

        EnchantOutcome outcome = service.enchant(player, "health potion of the bear");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("cannot bear a rune"), outcome.message());
    }

    @Test
    void enchantFailsWhenItemNotCarried() {
        Player player = withDust(player(100), 2);

        EnchantOutcome outcome = service.enchant(player, "iron sword of the bear");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("aren't carrying"), outcome.message());
    }

    @Test
    void enchantFailsForUnknownEnchantment() {
        Player player = withDust(player(100), 2).addItem(sword(List.of()));

        EnchantOutcome outcome = service.enchant(player, "iron sword of the dragon");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("no enchantment matching"), outcome.message());
    }

    @Test
    void enchantWithBlankInputPrompts() {
        EnchantOutcome outcome = service.enchant(player(100), "");

        assertFalse(outcome.success());
        assertTrue(outcome.message().startsWith("Enchant what?"), outcome.message());
    }

    @Test
    void formatRecipesShowsAffixBonusMaterialsAndGold() {
        Player player = withDust(player(100), 1).addItem(sword(List.of()));

        List<String> lines = service.formatRecipes(player);

        String body = String.join("\n", lines);
        assertTrue(body.contains("of the Bear"), body);
        assertTrue(body.contains("strength +2"), body);
        assertTrue(body.contains("have 1 / need 2"), body);
        assertTrue(body.contains("40 gold"), body);
    }
}
