package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.output.PlainTextStyler;
import io.taanielo.jmud.core.output.TextStyler;
import io.taanielo.jmud.core.world.Item;

/**
 * Pure, network-free helper that formats a player's carried inventory for the
 * {@code inventory} command.
 *
 * <p>Kept free of any I/O so the listing logic can be unit-tested in isolation.
 */
public final class InventoryListing {

    private static final String HEADER = "You are carrying:";
    private static final TextStyler PLAIN = new PlainTextStyler();

    private InventoryListing() {
    }

    /**
     * Formats the given item list and encumbrance values into display lines without rarity coloring.
     *
     * @param items         the items currently in the player's inventory
     * @param carriedWeight the sum of all item weights
     * @param maxCarry      the player's carry weight limit
     * @return the lines to render, never empty
     */
    public static List<String> format(List<Item> items, int carriedWeight, int maxCarry) {
        return format(items, carriedWeight, maxCarry, PLAIN);
    }

    /**
     * Formats the given item list and encumbrance values into display lines, coloring each item
     * name by its rarity tier through the supplied {@link TextStyler}.
     *
     * <p>The output starts with a header, lists each item with its weight, and
     * ends with a footer showing total carried weight and the carry limit. Rarity coloring composes
     * with the broken-item {@code (damaged)} annotation from {@link Item#durabilityDisplayName()}.
     *
     * @param items         the items currently in the player's inventory
     * @param carriedWeight the sum of all item weights
     * @param maxCarry      the player's carry weight limit
     * @param styler        the styler used to color item names by rarity
     * @return the lines to render, never empty
     */
    public static List<String> format(List<Item> items, int carriedWeight, int maxCarry, TextStyler styler) {
        Objects.requireNonNull(items, "Items are required");
        Objects.requireNonNull(styler, "Styler is required");
        List<String> lines = new ArrayList<>(items.size() + 2);
        lines.add(HEADER);
        if (items.isEmpty()) {
            lines.add("  (nothing)");
        } else {
            for (Item item : items) {
                Objects.requireNonNull(item, "Item must not be null");
                String name = styler.rarity(item.presentationName(), item.presentationRarity());
                lines.add(String.format("  %-30s %d lb", name, item.getWeight()));
            }
        }
        lines.add(String.format("Carrying %d / %d lbs.", carriedWeight, maxCarry));
        return List.copyOf(lines);
    }
}
