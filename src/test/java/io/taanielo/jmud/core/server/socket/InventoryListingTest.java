package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.output.AnsiTextStyler;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Rarity;

class InventoryListingTest {

    private static final String ESC = String.valueOf((char) 27);

    private static Item item(String id, String name, int weight) {
        return new Item(
            ItemId.of(id),
            name,
            "A " + name + ".",
            ItemAttributes.empty(),
            List.of(),
            List.of(),
            null,
            weight,
            0,
            null
        );
    }

    @Test
    void emptyInventoryShowsNothingAndWeight() {
        List<String> lines = InventoryListing.format(List.of(), 0, 100);

        assertEquals("You are carrying:", lines.get(0));
        assertEquals("  (nothing)", lines.get(1));
        assertTrue(lines.get(2).contains("0 / 100"), "Footer should show 0 / 100 but was: " + lines.get(2));
    }

    @Test
    void singleItemListedWithWeight() {
        Item sword = item("sword", "Iron Sword", 5);
        List<String> lines = InventoryListing.format(List.of(sword), 5, 50);

        assertEquals("You are carrying:", lines.get(0));
        assertTrue(lines.get(1).contains("Iron Sword"), "Should contain item name");
        assertTrue(lines.get(1).contains("5 lb"), "Should show item weight");
        assertTrue(lines.get(2).contains("5 / 50"), "Footer should show carried / max");
    }

    @Test
    void multipleItemsAllListed() {
        Item sword = item("sword", "Iron Sword", 5);
        Item shield = item("shield", "Wooden Shield", 3);
        List<String> lines = InventoryListing.format(List.of(sword, shield), 8, 50);

        assertEquals(4, lines.size(), "Header + 2 items + footer");
        assertTrue(lines.get(1).contains("Iron Sword"));
        assertTrue(lines.get(2).contains("Wooden Shield"));
        assertTrue(lines.get(3).contains("8 / 50"));
    }

    @Test
    void containerShownWithFillLevel() {
        Item contained = item("apple", "an apple", 1);
        Item bag = new Item(
            ItemId.of("leather-bag"),
            "a leather bag",
            "A supple leather bag.",
            ItemAttributes.empty(),
            List.of(),
            List.of(),
            null,
            1,
            0,
            null,
            null,
            5,
            List.of(contained)
        );

        List<String> lines = InventoryListing.format(List.of(bag), 1, 50);

        assertTrue(lines.get(1).contains("a leather bag (1/5)"),
            "Container line should show fill level but was: " + lines.get(1));
    }

    @Test
    void footerReflectsCarriedAndMaxWeight() {
        Item heavy = item("anvil", "Iron Anvil", 40);
        List<String> lines = InventoryListing.format(List.of(heavy), 40, 50);

        assertTrue(lines.getLast().contains("40 / 50 lbs"), "Footer: " + lines.getLast());
    }

    @Test
    void rareItemNameIsColoredUnderAnsiStyler() {
        Item blade = new Item(
            ItemId.of("blade"), "Runed Blade", "A glowing blade.",
            ItemAttributes.empty(), List.of(), List.of(), EquipmentSlot.WEAPON, 5, 0, null, null, null,
            List.of(), null, null, null, Rarity.RARE, List.of());

        List<String> lines = InventoryListing.format(List.of(blade), 5, 50, new AnsiTextStyler());

        assertTrue(lines.get(1).contains(ESC + "[36m"), "Rare item should be cyan-wrapped: " + lines.get(1));
        assertTrue(lines.get(1).contains("Runed Blade"), "Should still contain the item name");
    }
}
