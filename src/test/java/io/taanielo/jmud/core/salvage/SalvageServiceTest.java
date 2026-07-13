package io.taanielo.jmud.core.salvage;

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
import io.taanielo.jmud.core.world.Rarity;
import io.taanielo.jmud.core.world.RarityProfile;
import io.taanielo.jmud.core.world.repository.ItemRepository;

/**
 * Unit tests for {@link SalvageService}, exercising salvaging without any networking.
 */
class SalvageServiceTest {

    private static final ItemId IRON_ORE = ItemId.of("iron-ore");
    private static final ItemId ARCANE_DUST = ItemId.of("arcane-dust");
    private static final ItemId COMMON_SWORD = ItemId.of("common-sword");
    private static final ItemId UNCOMMON_SWORD = ItemId.of("uncommon-sword");
    private static final ItemId RARE_SWORD = ItemId.of("rare-sword");
    private static final ItemId EPIC_SWORD = ItemId.of("epic-sword");
    private static final ItemId POTION = ItemId.of("healing-potion");

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

    private final List<SalvageTier> tiers = List.of(
        new SalvageTier(Rarity.COMMON, List.of(new SalvageMaterial(IRON_ORE, 1))),
        new SalvageTier(Rarity.UNCOMMON,
            List.of(new SalvageMaterial(IRON_ORE, 2), new SalvageMaterial(ARCANE_DUST, 1))),
        new SalvageTier(Rarity.RARE,
            List.of(new SalvageMaterial(IRON_ORE, 3), new SalvageMaterial(ARCANE_DUST, 2))),
        new SalvageTier(Rarity.EPIC,
            List.of(new SalvageMaterial(IRON_ORE, 5), new SalvageMaterial(ARCANE_DUST, 4)))
    );

    private final SalvageService service = new SalvageService(tiers, itemRepository);

    SalvageServiceTest() {
        catalogue.put(IRON_ORE, material(IRON_ORE, "Iron Ore"));
        catalogue.put(ARCANE_DUST, material(ARCANE_DUST, "Arcane Dust"));
    }

    private static Item material(ItemId id, String name) {
        return Item.builder(id, name, "A material.", ItemAttributes.empty()).weight(1).value(2).build();
    }

    private static Item gear(ItemId id, String name, Rarity rarity) {
        return Item.builder(id, name, "A weapon.", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.WEAPON)
            .weight(3)
            .value(20)
            .rarity(RarityProfile.of(rarity, List.of()))
            .build();
    }

    private static Item potion() {
        return Item.builder(POTION, "healing potion", "Restores health.", ItemAttributes.empty())
            .weight(1)
            .value(10)
            .build();
    }

    private static Player player() {
        return Player.of(User.of(Username.of("hero"), Password.hash("pw", 1000)), "prompt");
    }

    private long count(Player player, ItemId id) {
        return player.getInventory().stream().filter(i -> i.getId().equals(id)).count();
    }

    @Test
    void salvageCommonYieldsBasicMaterial() {
        Player player = player().addItem(gear(COMMON_SWORD, "common sword", Rarity.COMMON));

        SalvageOutcome outcome = service.salvage(player, "common sword");

        assertTrue(outcome.success(), outcome.message());
        Player updated = outcome.updatedPlayer();
        assertEquals(0, count(updated, COMMON_SWORD), "salvaged item removed");
        assertEquals(1, count(updated, IRON_ORE), "one iron ore recovered");
        assertEquals(0, count(updated, ARCANE_DUST));
    }

    @Test
    void salvageUncommonYieldsMoreMaterials() {
        Player player = player().addItem(gear(UNCOMMON_SWORD, "uncommon sword", Rarity.UNCOMMON));

        SalvageOutcome outcome = service.salvage(player, "uncommon sword");

        assertTrue(outcome.success(), outcome.message());
        Player updated = outcome.updatedPlayer();
        assertEquals(2, count(updated, IRON_ORE));
        assertEquals(1, count(updated, ARCANE_DUST));
    }

    @Test
    void salvageRareYieldsBestMaterials() {
        Player player = player().addItem(gear(RARE_SWORD, "rare sword", Rarity.RARE));

        SalvageOutcome outcome = service.salvage(player, "rare sword");

        assertTrue(outcome.success(), outcome.message());
        Player updated = outcome.updatedPlayer();
        assertEquals(3, count(updated, IRON_ORE));
        assertEquals(2, count(updated, ARCANE_DUST));
    }

    @Test
    void salvageEpicYieldsSuperiorMaterials() {
        Player player = player().addItem(gear(EPIC_SWORD, "epic sword", Rarity.EPIC));

        SalvageOutcome outcome = service.salvage(player, "epic sword");

        assertTrue(outcome.success(), outcome.message());
        Player updated = outcome.updatedPlayer();
        assertEquals(0, count(updated, EPIC_SWORD), "salvaged item removed");
        assertEquals(5, count(updated, IRON_ORE), "epic yields more iron ore than rare");
        assertEquals(4, count(updated, ARCANE_DUST), "epic yields more arcane dust than rare");
    }

    @Test
    void salvageFailsWhenItemNotCarried() {
        SalvageOutcome outcome = service.salvage(player(), "phantom blade");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("not carrying"), outcome.message());
    }

    @Test
    void salvageFailsForEquippedItem() {
        Item sword = gear(COMMON_SWORD, "common sword", Rarity.COMMON);
        Player player = player().addItem(sword);
        player = player.withEquipment(player.getEquipment().equip(EquipmentSlot.WEAPON, sword.getId()));

        SalvageOutcome outcome = service.salvage(player, "common sword");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("UNEQUIP"), outcome.message());
    }

    @Test
    void salvageFailsForNonEquippableItem() {
        Player player = player().addItem(potion());

        SalvageOutcome outcome = service.salvage(player, "healing potion");

        assertFalse(outcome.success());
        assertNull(outcome.updatedPlayer());
        assertTrue(outcome.message().contains("cannot be salvaged"), outcome.message());
    }

    @Test
    void salvageWithBlankInputPromptsForItem() {
        SalvageOutcome outcome = service.salvage(player(), "");

        assertFalse(outcome.success());
        assertTrue(outcome.message().startsWith("Salvage what?"), outcome.message());
    }

    @Test
    void previewListsSalvageableCarriedItemsWithYield() {
        Player player = player()
            .addItem(gear(RARE_SWORD, "rare sword", Rarity.RARE))
            .addItem(potion());

        List<String> lines = service.preview(player);

        String body = String.join("\n", lines);
        assertTrue(body.contains("rare sword"), body);
        assertTrue(body.contains("Iron Ore"), body);
        assertTrue(body.contains("Arcane Dust"), body);
        assertFalse(body.contains("healing potion"), "non-gear excluded from preview: " + body);
    }

    @Test
    void previewExcludesEquippedItemsAndReportsWhenNoneSalvageable() {
        Item sword = gear(COMMON_SWORD, "common sword", Rarity.COMMON);
        Player player = player().addItem(sword);
        player = player.withEquipment(player.getEquipment().equip(EquipmentSlot.WEAPON, sword.getId()));

        List<String> lines = service.preview(player);

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("nothing worth salvaging"), lines.get(0));
    }
}
