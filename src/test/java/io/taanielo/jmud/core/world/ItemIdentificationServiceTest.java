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
        return new Item(
            ItemId.of(id), name, "A mysterious item.",
            ItemAttributes.empty(), List.of(), List.of(), EquipmentSlot.WEAPON, 5, 100, null, null, null,
            List.of(), null, null, null, Rarity.RARE, List.of(AffixId.of("of-the-titan")), false
        );
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
        Item item = new Item(
            ItemId.of("rock"), "a rock", "A rock.",
            ItemAttributes.empty(), List.of(), List.of(), null, 1, 5, null
        );
        assertSame(item, service.identify(item));
    }

    @Test
    void identifyInInventoryReplacesMatchingItemPreservingOrder() {
        Item first = new Item(ItemId.of("a"), "a", "A.", ItemAttributes.empty(), List.of(), List.of(), null, 1, 1, null);
        Item target = unidentified("blade", "a runed longsword");
        Item last = new Item(ItemId.of("z"), "z", "Z.", ItemAttributes.empty(), List.of(), List.of(), null, 1, 1, null);
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
        Item present = new Item(ItemId.of("a"), "a", "A.", ItemAttributes.empty(), List.of(), List.of(), null, 1, 1, null);
        Item missing = unidentified("blade", "a runed longsword");
        List<Item> inventory = List.of(present);

        assertSame(inventory, service.identifyInInventory(inventory, missing));
    }
}
