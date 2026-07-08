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
        return Item.builder(ItemId.of(id), name, "A thing.", ItemAttributes.empty())
            .weight(1)
            .value(5)
            .build();
    }

    private static Item container(String id, String name, int capacity) {
        return Item.builder(ItemId.of(id), name, "A container.", ItemAttributes.empty())
            .weight(1)
            .value(5)
            .container(ContainerState.of(capacity))
            .build();
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
        assertThrows(IllegalArgumentException.class, () -> Item.builder(
            ItemId.of("rock"), "a rock", "A rock.", ItemAttributes.empty())
            .weight(1)
            .value(5)
            .container(new ContainerState(null, List.of(plain("a", "an apple")), false))
            .build());
    }

    @Test
    void nonPositiveCapacityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Item.builder(
            ItemId.of("bag"), "a bag", "A bag.", ItemAttributes.empty())
            .weight(1)
            .value(5)
            .container(ContainerState.of(0))
            .build());
    }

    @Test
    void plainItemIsNotALightSource() {
        assertFalse(plain("rock", "a rock").isLightSource());
    }

    @Test
    void itemWithPositiveLightRadiusIsALightSource() {
        Item torch = Item.builder(ItemId.of("torch"), "a torch", "A torch.", ItemAttributes.empty())
            .weight(1)
            .value(5)
            .light(LightSource.of(1))
            .build();
        assertTrue(torch.isLightSource());
        assertEquals(1, torch.getLightRadius());
    }

    @Test
    void nonPositiveLightRadiusIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Item.builder(
            ItemId.of("torch"), "a torch", "A torch.", ItemAttributes.empty())
            .weight(1)
            .value(5)
            .light(LightSource.of(0))
            .build());
    }

    private static Item durable(String id, String name, Integer maxDurability, Integer durability) {
        return Item.builder(ItemId.of(id), name, "A blade.", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.WEAPON)
            .weight(5)
            .value(100)
            .durability(Durability.of(maxDurability, durability))
            .build();
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

    @Test
    void plainItemDefaultsToCommonRarityWithNoAffixes() {
        Item item = plain("rock", "a rock");
        assertEquals(Rarity.COMMON, item.getRarity());
        assertTrue(item.getAffixes().isEmpty());
    }

    @Test
    void itemRetainsRarityAndAffixes() {
        Item item = Item.builder(ItemId.of("blade"), "a blade", "A fine blade.", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.WEAPON)
            .weight(5)
            .value(100)
            .rarity(RarityProfile.of(Rarity.RARE, List.of(AffixId.of("of-the-bear"), AffixId.of("of-vitality"))))
            .build();
        assertEquals(Rarity.RARE, item.getRarity());
        assertEquals(List.of(AffixId.of("of-the-bear"), AffixId.of("of-vitality")), item.getAffixes());
    }

    @Test
    void nullRarityDefaultsToCommon() {
        Item item = Item.builder(ItemId.of("blade"), "a blade", "A fine blade.", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.WEAPON)
            .weight(5)
            .value(100)
            .rarity(RarityProfile.of(null, null))
            .build();
        assertEquals(Rarity.COMMON, item.getRarity());
        assertTrue(item.getAffixes().isEmpty());
    }

    @Test
    void rarityAndAffixesSurviveWithContainedItemsCopy() {
        Item chest = Item.builder(ItemId.of("chest"), "a chest", "A sturdy chest.", ItemAttributes.empty())
            .weight(1)
            .value(5)
            .container(ContainerState.of(3))
            .rarity(RarityProfile.of(Rarity.UNCOMMON, List.of(AffixId.of("of-the-fox"))))
            .build();
        Item withItem = chest.withContainedItem(plain("a", "an apple"));
        assertEquals(Rarity.UNCOMMON, withItem.getRarity());
        assertEquals(List.of(AffixId.of("of-the-fox")), withItem.getAffixes());
    }

    private static Item unidentifiedRare(String id, String name) {
        return Item.builder(ItemId.of(id), name, "A mysterious item.", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.WEAPON)
            .weight(5)
            .value(100)
            .rarity(RarityProfile.of(Rarity.RARE, List.of(AffixId.of("of-the-titan"))))
            .identification(Identification.unidentified())
            .build();
    }

    @Test
    void itemsDefaultToIdentified() {
        assertTrue(plain("rock", "a rock").isIdentified());
    }

    @Test
    void unidentifiedItemHidesNameAndRarity() {
        Item blade = unidentifiedRare("blade", "a runed longsword");
        assertFalse(blade.isIdentified());
        assertEquals("an unidentified runed longsword", blade.presentationName());
        assertEquals(Rarity.COMMON, blade.presentationRarity());
    }

    @Test
    void identifyingRevealsRealNameAndRarity() {
        Item revealed = unidentifiedRare("blade", "a runed longsword").withIdentified(true);
        assertTrue(revealed.isIdentified());
        assertEquals("a runed longsword", revealed.presentationName());
        assertEquals(Rarity.RARE, revealed.presentationRarity());
        assertEquals(List.of(AffixId.of("of-the-titan")), revealed.getAffixes());
    }

    @Test
    void withIdentifiedReturnsSameInstanceWhenUnchanged() {
        Item blade = unidentifiedRare("blade", "a runed longsword");
        assertTrue(blade == blade.withIdentified(false));
    }

    @Test
    void unidentifiedNameStripsLeadingArticle() {
        Item withThe = Item.builder(ItemId.of("crown"), "the Crown of Kings", "A regal crown.", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.HEAD)
            .weight(2)
            .value(500)
            .rarity(RarityProfile.of(Rarity.RARE, List.of()))
            .identification(Identification.unidentified())
            .build();
        assertEquals("an unidentified Crown of Kings", withThe.presentationName());
    }
}
