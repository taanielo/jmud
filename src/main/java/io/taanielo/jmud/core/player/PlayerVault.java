package io.taanielo.jmud.core.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.world.Item;

/**
 * Immutable holder for the items a player has stored in their personal bank vault.
 *
 * <p>Vaulted items are kept safe from death, corpse decay and corpse looting: they are
 * not part of carried inventory while stored. Capacity limits are enforced by
 * {@code io.taanielo.jmud.core.bank.BankService}, not here — this component is a plain
 * value holder mirroring {@link PlayerInventory}.
 */
public class PlayerVault {
    private final List<Item> items;

    public PlayerVault(List<Item> items) {
        this.items = List.copyOf(Objects.requireNonNullElse(items, List.of()));
    }

    /** Returns an empty vault. */
    public static PlayerVault empty() {
        return new PlayerVault(List.of());
    }

    /** Returns the stored items in insertion order. */
    public List<Item> items() {
        return items;
    }

    /** Returns the number of items currently stored. */
    public int size() {
        return items.size();
    }

    /** Returns a copy of this vault with the given item list replacing the current one. */
    public PlayerVault withItems(List<Item> nextItems) {
        return new PlayerVault(nextItems);
    }

    /** Returns a copy of this vault with the given item added. */
    public PlayerVault addItem(Item item) {
        Objects.requireNonNull(item, "Item is required");
        List<Item> next = new ArrayList<>(items);
        next.add(item);
        return new PlayerVault(next);
    }

    /** Returns a copy of this vault with the first item matching the given item's id removed. */
    public PlayerVault removeItem(Item item) {
        Objects.requireNonNull(item, "Item is required");
        List<Item> next = new ArrayList<>(items);
        boolean removed = next.removeIf(existing -> existing.getId().equals(item.getId()));
        if (!removed) {
            return this;
        }
        return new PlayerVault(next);
    }
}
