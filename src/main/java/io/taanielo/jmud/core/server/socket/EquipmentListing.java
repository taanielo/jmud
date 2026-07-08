package io.taanielo.jmud.core.server.socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

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

    private EquipmentListing() {
    }

    /**
     * Formats the equipment slots into display lines.
     *
     * <p>Every {@link EquipmentSlot} is shown in declaration order. Equipped
     * slots display the item name; empty slots display {@code (empty)}.
     *
     * @param slots    the map of currently equipped item ids keyed by slot
     * @param itemById a lookup function mapping an {@link ItemId} to an {@link Item},
     *                 used to resolve the display name; returns {@code null} when unknown
     * @return the lines to render, never empty
     */
    public static List<String> format(Map<EquipmentSlot, ItemId> slots, Function<ItemId, Item> itemById) {
        Objects.requireNonNull(slots, "Slots are required");
        Objects.requireNonNull(itemById, "Item lookup is required");
        List<String> lines = new ArrayList<>(EquipmentSlot.values().length + 1);
        lines.add(HEADER);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemId equipped = slots.get(slot);
            String label = capitalize(slot.id());
            if (equipped == null) {
                lines.add(String.format("  %-8s : %s", label, EMPTY_SLOT));
            } else {
                Item item = itemById.apply(equipped);
                String itemName = item != null ? item.durabilityDisplayName() : equipped.getValue();
                lines.add(String.format("  %-8s : %s", label, itemName));
            }
        }
        return List.copyOf(lines);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
