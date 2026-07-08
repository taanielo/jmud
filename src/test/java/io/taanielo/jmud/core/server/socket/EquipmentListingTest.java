package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.output.AnsiTextStyler;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Rarity;
import io.taanielo.jmud.core.world.RarityProfile;

class EquipmentListingTest {

    private static final String ESC = String.valueOf((char) 27);

    private static Item item(String id, String name, EquipmentSlot slot) {
        return Item.builder(ItemId.of(id), name, "A " + name + ".", ItemAttributes.empty())
            .equipSlot(slot)
            .weight(1)
            .value(0)
            .build();
    }

    @Test
    void allSlotsShownWhenNothingEquipped() {
        List<String> lines = EquipmentListing.format(Map.of(), id -> null);

        assertEquals("You are wearing:", lines.get(0));
        assertEquals(EquipmentSlot.values().length + 1, lines.size(), "Header plus one line per slot");
        for (String line : lines.subList(1, lines.size())) {
            assertTrue(line.contains("(empty)"), "All slots should be empty: " + line);
        }
    }

    @Test
    void equippedSlotShowsItemName() {
        ItemId swordId = ItemId.of("sword");
        Item sword = item("sword", "Iron Sword", EquipmentSlot.WEAPON);
        List<String> lines = EquipmentListing.format(
            Map.of(EquipmentSlot.WEAPON, swordId),
            id -> id.equals(swordId) ? sword : null
        );

        String weaponLine = lines.stream()
            .filter(l -> l.contains("Weapon"))
            .findFirst()
            .orElseThrow();
        assertTrue(weaponLine.contains("Iron Sword"), "Weapon slot should show item name: " + weaponLine);
    }

    @Test
    void unknownItemIdFallsBackToId() {
        ItemId unknownId = ItemId.of("unknown-item");
        List<String> lines = EquipmentListing.format(
            Map.of(EquipmentSlot.HEAD, unknownId),
            id -> null
        );

        String headLine = lines.stream()
            .filter(l -> l.contains("Head"))
            .findFirst()
            .orElseThrow();
        assertTrue(headLine.contains("unknown-item"), "Should fall back to item id: " + headLine);
    }

    @Test
    void multipleEquippedSlotsAllRendered() {
        ItemId swordId = ItemId.of("sword");
        ItemId helmetId = ItemId.of("helmet");
        Item sword = item("sword", "Iron Sword", EquipmentSlot.WEAPON);
        Item helmet = item("helmet", "Iron Helmet", EquipmentSlot.HEAD);

        Map<EquipmentSlot, ItemId> slots = Map.of(
            EquipmentSlot.WEAPON, swordId,
            EquipmentSlot.HEAD, helmetId
        );
        List<String> lines = EquipmentListing.format(slots, id -> {
            if (id.equals(swordId)) return sword;
            if (id.equals(helmetId)) return helmet;
            return null;
        });

        assertTrue(lines.stream().anyMatch(l -> l.contains("Iron Sword")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("Iron Helmet")));
        // remaining slots empty
        long emptyCount = lines.stream().filter(l -> l.contains("(empty)")).count();
        assertEquals(EquipmentSlot.values().length - 2, emptyCount);
    }

    @Test
    void uncommonItemNameIsColoredUnderAnsiStyler() {
        ItemId swordId = ItemId.of("sword");
        Item sword = Item.builder(swordId, "Keen Sword", "A keen sword.", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.WEAPON)
            .weight(1)
            .value(0)
            .rarity(RarityProfile.of(Rarity.UNCOMMON, List.of()))
            .build();

        List<String> lines = EquipmentListing.format(
            Map.of(EquipmentSlot.WEAPON, swordId),
            id -> id.equals(swordId) ? sword : null,
            new AnsiTextStyler());

        String weaponLine = lines.stream().filter(l -> l.contains("Weapon")).findFirst().orElseThrow();
        assertTrue(weaponLine.contains(ESC + "[32m"), "Uncommon item should be green-wrapped: " + weaponLine);
        assertTrue(weaponLine.contains("Keen Sword"), "Should still contain the item name");
    }
}
