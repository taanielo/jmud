package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ItemIdentificationService}.
 */
class ItemIdentificationServiceTest {

    private final ItemIdentificationService service = new ItemIdentificationService();

    private static Item unidentified(String id, String name) {
        return Item.builder(ItemId.of(id), name, "A mysterious item.", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.WEAPON)
            .weight(5)
            .value(100)
            .rarity(RarityProfile.of(Rarity.RARE, List.of(AffixId.of("of-the-titan"))))
            .identification(Identification.unidentified())
            .build();
    }

    @Test
    void identifyMarksItemIdentified() {
        Item item = unidentified("blade", "a runed longsword");
        assertFalse(item.isIdentified());

        Item revealed = service.identify(item);

        assertTrue(revealed.isIdentified());
        assertEquals(Rarity.RARE, revealed.getRarity());
        assertEquals(List.of(AffixId.of("of-the-titan")), revealed.getAffixes());
    }

    @Test
    void identifyAlreadyIdentifiedReturnsSameInstance() {
        Item item = Item.builder(ItemId.of("rock"), "a rock", "A rock.", ItemAttributes.empty())
            .weight(1)
            .value(5)
            .build();
        assertSame(item, service.identify(item));
    }

    @Test
    void identifyInInventoryReplacesMatchingItemPreservingOrder() {
        Item first = Item.builder(ItemId.of("a"), "a", "A.", ItemAttributes.empty()).weight(1).value(1).build();
        Item target = unidentified("blade", "a runed longsword");
        Item last = Item.builder(ItemId.of("z"), "z", "Z.", ItemAttributes.empty()).weight(1).value(1).build();
        List<Item> inventory = List.of(first, target, last);

        List<Item> next = service.identifyInInventory(inventory, target);

        assertEquals(3, next.size());
        assertSame(first, next.get(0));
        assertTrue(next.get(1).isIdentified());
        assertEquals(ItemId.of("blade"), next.get(1).getId());
        assertSame(last, next.get(2));
    }

    @Test
    void identifyInInventoryReturnsOriginalWhenAbsent() {
        Item present = Item.builder(ItemId.of("a"), "a", "A.", ItemAttributes.empty()).weight(1).value(1).build();
        Item missing = unidentified("blade", "a runed longsword");
        List<Item> inventory = List.of(present);

        assertSame(inventory, service.identifyInInventory(inventory, missing));
    }
}
