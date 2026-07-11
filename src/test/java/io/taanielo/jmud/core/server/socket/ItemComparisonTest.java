package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.output.AnsiTextStyler;
import io.taanielo.jmud.core.world.Durability;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Rarity;
import io.taanielo.jmud.core.world.RarityProfile;

/**
 * Unit tests for the pure {@link ItemComparison} formatter.
 */
class ItemComparisonTest {

    private static final String ESC = String.valueOf((char) 27);

    /** Resolver that just reads each item's declared base stats — no affix service needed. */
    private static Map<String, Integer> baseStats(Item item) {
        return item.getAttributes().getStats();
    }

    private static Item weapon(String id, String name, Map<String, Integer> stats, int weight, int value) {
        return Item.builder(ItemId.of(id), name, "A " + name + ".", new ItemAttributes(stats))
            .equipSlot(EquipmentSlot.WEAPON)
            .weight(weight)
            .value(value)
            .build();
    }

    @Test
    void emptySlotShowsCandidateStatsWithNote() {
        Item candidate = weapon("sword", "Iron Sword", Map.of("strength", 3), 5, 40);

        List<String> lines = ItemComparison.format(candidate, Optional.empty(), ItemComparisonTest::baseStats);

        assertTrue(lines.stream().anyMatch(l -> l.contains("Iron Sword")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("Nothing is currently equipped")),
            "Empty slot should be called out: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.contains("strength") && l.contains("3")));
        assertFalse(lines.stream().anyMatch(l -> l.contains("->")),
            "Empty-slot listing has no delta arrows: " + lines);
    }

    @Test
    void occupiedSlotShowsPerStatDelta() {
        Item equipped = weapon("old", "Rusty Sword", Map.of("strength", 3), 5, 20);
        Item candidate = weapon("new", "Keen Sword", Map.of("strength", 5), 4, 60);

        List<String> lines = ItemComparison.format(candidate, Optional.of(equipped), ItemComparisonTest::baseStats);

        String strengthLine = lines.stream()
            .filter(l -> l.contains("strength"))
            .findFirst()
            .orElseThrow();
        assertTrue(strengthLine.contains("3 -> 5"), "Should show equipped -> candidate: " + strengthLine);
        assertTrue(strengthLine.contains("(+2)"), "Should show positive delta: " + strengthLine);
    }

    @Test
    void statOnlyOnOneItemTreatsMissingAsZero() {
        Item equipped = weapon("old", "Plain Sword", Map.of("strength", 4), 5, 20);
        Item candidate = weapon("new", "Agile Dagger", Map.of("dexterity", 2), 2, 30);

        List<String> lines = ItemComparison.format(candidate, Optional.of(equipped), ItemComparisonTest::baseStats);

        String strengthLine = lines.stream().filter(l -> l.contains("strength")).findFirst().orElseThrow();
        assertTrue(strengthLine.contains("4 -> 0") && strengthLine.contains("(-4)"),
            "Losing a stat should read 4 -> 0 (-4): " + strengthLine);
        String dexterityLine = lines.stream().filter(l -> l.contains("dexterity")).findFirst().orElseThrow();
        assertTrue(dexterityLine.contains("0 -> 2") && dexterityLine.contains("(+2)"),
            "Gaining a stat should read 0 -> 2 (+2): " + dexterityLine);
    }

    @Test
    void affixBonusIsReflectedThroughResolver() {
        // Candidate's base strength is 3, but the resolver folds in a +2 affix bonus to 5.
        Item equipped = weapon("old", "Plain Sword", Map.of("strength", 3), 5, 20);
        Item candidate = weapon("new", "Enchanted Sword", Map.of("strength", 3), 5, 80);
        Function<Item, Map<String, Integer>> resolver = item ->
            item.getId().getValue().equals("new") ? Map.of("strength", 5) : item.getAttributes().getStats();

        List<String> lines = ItemComparison.format(candidate, Optional.of(equipped), resolver);

        String strengthLine = lines.stream().filter(l -> l.contains("strength")).findFirst().orElseThrow();
        assertTrue(strengthLine.contains("3 -> 5") && strengthLine.contains("(+2)"),
            "Affix bonus should surface in the delta: " + strengthLine);
    }

    @Test
    void showsWeightAndValueForBothItems() {
        Item equipped = weapon("old", "Rusty Sword", Map.of(), 5, 20);
        Item candidate = weapon("new", "Keen Sword", Map.of(), 4, 60);

        List<String> lines = ItemComparison.format(candidate, Optional.of(equipped), ItemComparisonTest::baseStats);

        assertTrue(lines.stream().anyMatch(l -> l.contains("weight") && l.contains("5 -> 4")),
            "Weight diff expected: " + lines);
        assertTrue(lines.stream().anyMatch(l -> l.contains("value") && l.contains("20 -> 60")),
            "Value diff expected: " + lines);
    }

    @Test
    void showsDurabilityWhenEitherBreakable() {
        Item equipped = Item.builder(ItemId.of("old"), "Worn Blade", "A worn blade.", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.WEAPON)
            .weight(5)
            .value(20)
            .durability(Durability.of(50, 10))
            .build();
        Item candidate = Item.builder(ItemId.of("new"), "Fresh Blade", "A fresh blade.", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.WEAPON)
            .weight(5)
            .value(60)
            .durability(Durability.of(80, 80))
            .build();

        List<String> lines = ItemComparison.format(candidate, Optional.of(equipped), ItemComparisonTest::baseStats);

        assertTrue(lines.stream().anyMatch(l -> l.contains("durability") && l.contains("10/50 -> 80/80")),
            "Durability current/max for both items expected: " + lines);
    }

    @Test
    void unbreakableItemsOmitDurabilityLine() {
        Item equipped = weapon("old", "Rusty Sword", Map.of(), 5, 20);
        Item candidate = weapon("new", "Keen Sword", Map.of(), 4, 60);

        List<String> lines = ItemComparison.format(candidate, Optional.of(equipped), ItemComparisonTest::baseStats);

        assertFalse(lines.stream().anyMatch(l -> l.contains("durability")),
            "No durability line when neither item is breakable: " + lines);
    }

    @Test
    void rarityColoringAppliedThroughStyler() {
        Item equipped = weapon("old", "Rusty Sword", Map.of(), 5, 20);
        Item candidate = Item.builder(ItemId.of("new"), "Keen Sword", "A keen sword.", ItemAttributes.empty())
            .equipSlot(EquipmentSlot.WEAPON)
            .weight(4)
            .value(60)
            .rarity(RarityProfile.of(Rarity.UNCOMMON, List.of()))
            .build();

        List<String> lines = ItemComparison.format(
            candidate, Optional.of(equipped), ItemComparisonTest::baseStats, new AnsiTextStyler());

        String header = lines.get(0);
        assertTrue(header.contains(ESC + "[32m"), "Uncommon candidate should be green-wrapped: " + header);
        assertTrue(header.contains("Keen Sword"));
    }
}
