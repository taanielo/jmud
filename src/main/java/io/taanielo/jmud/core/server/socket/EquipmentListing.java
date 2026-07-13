package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.taanielo.jmud.core.output.PlainTextStyler;
import io.taanielo.jmud.core.output.TextStyler;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;

/**
 * Pure, network-free helper that formats a player's worn equipment for the
 * {@code equipment} command.
 *
 * <p>Kept free of any I/O so the listing logic can be unit-tested in isolation.
 */
public final class EquipmentListing {

    private static final String HEADER = "You are wearing:";
    private static final String EMPTY_SLOT = "(empty)";
    private static final String RESIST_STAT_SUFFIX = "_resist";
    private static final TextStyler PLAIN = new PlainTextStyler();

    private EquipmentListing() {
    }

    /**
     * Formats the equipment slots into display lines without rarity coloring.
     *
     * @param slots    the map of currently equipped item ids keyed by slot
     * @param itemById a lookup function mapping an {@link ItemId} to an {@link Item},
     *                 used to resolve the display name; returns {@code null} when unknown
     * @return the lines to render, never empty
     */
    public static List<String> format(Map<EquipmentSlot, ItemId> slots, Function<ItemId, Item> itemById) {
        return format(slots, itemById, PLAIN);
    }

    /**
     * Formats the equipment slots into display lines, coloring each equipped item name by its
     * rarity tier through the supplied {@link TextStyler}.
     *
     * <p>Every {@link EquipmentSlot} is shown in declaration order. Equipped
     * slots display the item name (rarity-colored and composing with the {@code (damaged)}
     * annotation from {@link Item#durabilityDisplayName()}); empty slots display {@code (empty)}.
     *
     * @param slots    the map of currently equipped item ids keyed by slot
     * @param itemById a lookup function mapping an {@link ItemId} to an {@link Item},
     *                 used to resolve the display name; returns {@code null} when unknown
     * @param styler   the styler used to color item names by rarity
     * @return the lines to render, never empty
     */
    public static List<String> format(
        Map<EquipmentSlot, ItemId> slots, Function<ItemId, Item> itemById, TextStyler styler) {
        Objects.requireNonNull(slots, "Slots are required");
        Objects.requireNonNull(itemById, "Item lookup is required");
        Objects.requireNonNull(styler, "Styler is required");
        List<String> lines = new ArrayList<>(EquipmentSlot.values().length + 1);
        lines.add(HEADER);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemId equipped = slots.get(slot);
            String label = capitalize(slot.id());
            if (equipped == null) {
                lines.add(String.format("  %-8s : %s", label, EMPTY_SLOT));
            } else {
                Item item = itemById.apply(equipped);
                String itemName = item != null
                    ? styler.rarity(item.presentationName(), item.presentationRarity())
                    : equipped.getValue();
                lines.add(String.format("  %-8s : %s%s", label, itemName, resistSuffix(item)));
            }
        }
        return List.copyOf(lines);
    }

    /**
     * Renders the elemental-resistance annotation for an equipped item, e.g. {@code " [cold_resist
     * 25, fire_resist 10]"}, so a player can see at a glance which resistances their worn gear
     * provides. Returns an empty string when the item is unknown or carries no {@code *_resist}
     * stat, leaving ordinary gear lines unchanged.
     */
    private static String resistSuffix(Item item) {
        if (item == null || item.getAttributes() == null) {
            return "";
        }
        Map<String, Integer> stats = item.getAttributes().getStats();
        if (stats == null || stats.isEmpty()) {
            return "";
        }
        String resists = new TreeMap<>(stats).entrySet().stream()
            .filter(e -> e.getKey().endsWith(RESIST_STAT_SUFFIX) && e.getValue() != null && e.getValue() != 0)
            .map(e -> e.getKey() + " " + e.getValue())
            .collect(Collectors.joining(", "));
        return resists.isEmpty() ? "" : " [" + resists + "]";
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
