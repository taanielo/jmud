package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the container behaviour on {@link Item}.
 */
class ItemTest {

    private static Item plain(String id, String name) {
        return new Item(
            ItemId.of(id), name, "A thing.",
            ItemAttributes.empty(), List.of(), List.of(), null, 1, 5, null
        );
    }

    private static Item container(String id, String name, int capacity) {
        return new Item(
            ItemId.of(id), name, "A container.",
            ItemAttributes.empty(), List.of(), List.of(), null, 1, 5, null, null, capacity, List.of()
        );
    }

    @Test
    void plainItemIsNotAContainer() {
        Item item = plain("rock", "a rock");
        assertFalse(item.isContainer());
        assertFalse(item.isFull());
        assertEquals(0, item.containedItemCount());
        assertEquals("a rock", item.displayName());
    }

    @Test
    void containerReportsFillLevelInDisplayName() {
        Item bag = container("bag", "a leather bag", 5)
            .withContainedItem(plain("a", "a"))
            .withContainedItem(plain("b", "b"))
            .withContainedItem(plain("c", "c"));
        assertTrue(bag.isContainer());
        assertEquals(3, bag.containedItemCount());
        assertEquals("a leather bag (3/5)", bag.displayName());
    }

    @Test
    void addingUpToCapacityMarksFull() {
        Item bag = container("bag", "a bag", 2)
            .withContainedItem(plain("a", "a"))
            .withContainedItem(plain("b", "b"));
        assertTrue(bag.isFull());
    }

    @Test
    void addingBeyondCapacityIsRejected() {
        Item bag = container("bag", "a bag", 1).withContainedItem(plain("a", "a"));
        assertThrows(IllegalStateException.class, () -> bag.withContainedItem(plain("b", "b")));
    }

    @Test
    void removingItemFreesSlot() {
        Item bag = container("bag", "a bag", 1).withContainedItem(plain("a", "an apple"));
        assertTrue(bag.isFull());

        Item emptied = bag.withoutContainedItem(ItemId.of("a"));
        assertFalse(emptied.isFull());
        assertEquals(0, emptied.containedItemCount());
        // The freed container can accept another item.
        assertEquals(1, emptied.withContainedItem(plain("b", "a pear")).containedItemCount());
    }

    @Test
    void removingMissingItemReturnsSameContents() {
        Item bag = container("bag", "a bag", 3).withContainedItem(plain("a", "an apple"));
        Item unchanged = bag.withoutContainedItem(ItemId.of("nope"));
        assertEquals(1, unchanged.containedItemCount());
    }

    @Test
    void nestingContainersIsRejected() {
        Item outer = container("outer", "a chest", 3);
        Item inner = container("inner", "a bag", 2);
        assertThrows(IllegalArgumentException.class, () -> outer.withContainedItem(inner));
    }

    @Test
    void nonContainerCannotHoldContents() {
        assertThrows(IllegalArgumentException.class, () -> new Item(
            ItemId.of("rock"), "a rock", "A rock.",
            ItemAttributes.empty(), List.of(), List.of(), null, 1, 5, null, null, null,
            List.of(plain("a", "an apple"))
        ));
    }

    @Test
    void nonPositiveCapacityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Item(
            ItemId.of("bag"), "a bag", "A bag.",
            ItemAttributes.empty(), List.of(), List.of(), null, 1, 5, null, null, 0, List.of()
        ));
    }

    @Test
    void plainItemIsNotALightSource() {
        assertFalse(plain("rock", "a rock").isLightSource());
    }

    @Test
    void itemWithPositiveLightRadiusIsALightSource() {
        Item torch = new Item(
            ItemId.of("torch"), "a torch", "A torch.",
            ItemAttributes.empty(), List.of(), List.of(), null, 1, 5, null, null, null, List.of(), 1
        );
        assertTrue(torch.isLightSource());
        assertEquals(1, torch.getLightRadius());
    }

    @Test
    void nonPositiveLightRadiusIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Item(
            ItemId.of("torch"), "a torch", "A torch.",
            ItemAttributes.empty(), List.of(), List.of(), null, 1, 5, null, null, null, List.of(), 0
        ));
    }

    private static Item durable(String id, String name, Integer maxDurability, Integer durability) {
        return new Item(
            ItemId.of(id), name, "A blade.",
            ItemAttributes.empty(), List.of(), List.of(), EquipmentSlot.WEAPON, 5, 100, null, null, null,
            List.of(), null, maxDurability, durability
        );
    }

    @Test
    void plainItemIsNotBreakable() {
        Item item = plain("rock", "a rock");
        assertFalse(item.isBreakable());
        assertFalse(item.isBroken());
    }

    @Test
    void breakableItemDefaultsToFullDurability() {
        Item sword = durable("sword", "a sword", 50, null);
        assertTrue(sword.isBreakable());
        assertFalse(sword.isBroken());
        assertEquals(50, sword.getDurability());
        assertEquals(50, sword.getMaxDurability());
    }

    @Test
    void durabilityAboveMaxIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> durable("sword", "a sword", 10, 11));
    }

    @Test
    void durabilityWithoutMaxIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> durable("sword", "a sword", null, 5));
    }

    @Test
    void withDurabilityClampsToRange() {
        Item sword = durable("sword", "a sword", 50, 50);
        assertEquals(0, sword.withDurability(-5).getDurability());
        assertEquals(50, sword.withDurability(999).getDurability());
        assertEquals(20, sword.withDurability(20).getDurability());
    }

    @Test
    void brokenItemAnnotatesDisplayName() {
        Item sword = durable("sword", "a sword", 50, 0);
        assertTrue(sword.isBroken());
        assertEquals("a sword (damaged)", sword.durabilityDisplayName());
    }

    @Test
    void healthyBreakableItemHasPlainDisplayName() {
        Item sword = durable("sword", "a sword", 50, 50);
        assertEquals("a sword", sword.durabilityDisplayName());
    }

    @Test
    void withDurabilityOnUnbreakableItemIsRejected() {
        assertThrows(IllegalStateException.class, () -> plain("rock", "a rock").withDurability(1));
    }
}
