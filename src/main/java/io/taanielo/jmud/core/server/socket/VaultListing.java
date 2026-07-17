package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.output.PlainTextStyler;
import io.taanielo.jmud.core.output.TextStyler;
import io.taanielo.jmud.core.world.Item;

/**
 * Pure, network-free helper that formats a player's bank vault contents for the
 * {@code vault} command, mirroring {@link InventoryListing}'s formatting.
 *
 * <p>Kept free of any I/O so the listing logic can be unit-tested in isolation.
 */
public final class VaultListing {

    private static final String HEADER = "Your vault contains:";
    private static final String EMPTY = "Your vault is empty.";
    private static final TextStyler PLAIN = new PlainTextStyler();

    private VaultListing() {
    }

    /**
     * Formats the given vault item list into display lines without rarity coloring.
     *
     * @param items    the items stored in the player's vault
     * @param capacity the maximum number of items the vault can hold
     * @return the lines to render, never empty
     */
    public static List<String> format(List<Item> items, int capacity) {
        return format(items, capacity, PLAIN);
    }

    /**
     * Formats the given vault item list into display lines, coloring each item name by its rarity
     * tier through the supplied {@link TextStyler}.
     *
     * <p>When the vault is empty a single "Your vault is empty." line is returned. Otherwise the
     * output starts with a header, lists each item with its weight, and ends with a footer showing
     * used and total slots.
     *
     * @param items    the items stored in the player's vault
     * @param capacity the maximum number of items the vault can hold
     * @param styler   the styler used to color item names by rarity
     * @return the lines to render, never empty
     */
    public static List<String> format(List<Item> items, int capacity, TextStyler styler) {
        Objects.requireNonNull(items, "Items are required");
        Objects.requireNonNull(styler, "Styler is required");
        if (items.isEmpty()) {
            return List.of(EMPTY);
        }
        List<String> lines = new ArrayList<>(items.size() + 2);
        lines.add(HEADER);
        for (Item item : items) {
            Objects.requireNonNull(item, "Item must not be null");
            String name = styler.rarity(item.presentationName(), item.presentationRarity());
            lines.add(String.format("  %-30s %d lb", name, item.getWeight()));
        }
        lines.add(String.format("Stored %d / %d items.", items.size(), capacity));
        return List.copyOf(lines);
    }

    /**
     * Formats the given vault item list into display lines, always showing a slots-used vs. capacity
     * footer (even for an empty vault) and an optional upgrade hint line.
     *
     * @param items        the items stored in the player's vault
     * @param capacity     the player's effective vault capacity
     * @param styler       the styler used to color item names by rarity
     * @param upgradeHint  a line describing the next upgrade tier's cost and slot gain, or
     *                     {@code null}/blank to omit it (e.g. when already at the top tier)
     * @return the lines to render, never empty
     */
    public static List<String> format(List<Item> items, int capacity, TextStyler styler, String upgradeHint) {
        Objects.requireNonNull(items, "Items are required");
        Objects.requireNonNull(styler, "Styler is required");
        List<String> lines = new ArrayList<>(items.size() + 3);
        if (items.isEmpty()) {
            lines.add(EMPTY);
        } else {
            lines.add(HEADER);
            for (Item item : items) {
                Objects.requireNonNull(item, "Item must not be null");
                String name = styler.rarity(item.presentationName(), item.presentationRarity());
                lines.add(String.format("  %-30s %d lb", name, item.getWeight()));
            }
        }
        lines.add(String.format("Stored %d / %d items.", items.size(), capacity));
        if (upgradeHint != null && !upgradeHint.isBlank()) {
            lines.add(upgradeHint);
        }
        return List.copyOf(lines);
    }
}
