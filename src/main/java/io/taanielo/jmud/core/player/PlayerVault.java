package io.taanielo.jmud.core.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.world.Item;

/**
 * Immutable holder for the items a player has stored in their personal bank vault, together with
 * the player's purchased vault-capacity tier.
 *
 * <p>Vaulted items are kept safe from death, corpse decay and corpse looting: they are
 * not part of carried inventory while stored. Capacity limits are enforced by
 * {@code io.taanielo.jmud.core.bank.BankService}, not here — this component is a plain
 * value holder mirroring {@link PlayerInventory}.
 *
 * <p>The {@link #capacityTier() capacity tier} is a persisted, per-player value that starts at
 * {@code 0} (today's flat default capacity) and can be raised by paying gold at a bank via
 * {@code VAULT UPGRADE}. It only ever grows; the effective slot count is derived from it by
 * {@code BankService}.
 */
public class PlayerVault {
    private final List<Item> items;
    private final int capacityTier;

    public PlayerVault(List<Item> items) {
        this(items, 0);
    }

    public PlayerVault(List<Item> items, int capacityTier) {
        this.items = List.copyOf(Objects.requireNonNullElse(items, List.of()));
        this.capacityTier = Math.max(0, capacityTier);
    }

    /** Returns an empty vault at capacity tier 0. */
    public static PlayerVault empty() {
        return new PlayerVault(List.of(), 0);
    }

    /** Returns the stored items in insertion order. */
    public List<Item> items() {
        return items;
    }

    /** Returns the number of items currently stored. */
    public int size() {
        return items.size();
    }

    /** Returns the player's purchased vault-capacity tier; {@code 0} is the default. */
    public int capacityTier() {
        return capacityTier;
    }

    /** Returns a copy of this vault with the given item list replacing the current one. */
    public PlayerVault withItems(List<Item> nextItems) {
        return new PlayerVault(nextItems, capacityTier);
    }

    /**
     * Returns a copy of this vault at the given capacity tier, keeping the stored items unchanged.
     *
     * @param nextTier the new capacity tier; negative values are clamped to {@code 0}
     */
    public PlayerVault withCapacityTier(int nextTier) {
        return new PlayerVault(items, nextTier);
    }

    /** Returns a copy of this vault with the given item added. */
    public PlayerVault addItem(Item item) {
        Objects.requireNonNull(item, "Item is required");
        List<Item> next = new ArrayList<>(items);
        next.add(item);
        return new PlayerVault(next, capacityTier);
    }

    /** Returns a copy of this vault with the first item matching the given item's id removed. */
    public PlayerVault removeItem(Item item) {
        Objects.requireNonNull(item, "Item is required");
        List<Item> next = new ArrayList<>(items);
        boolean removed = next.removeIf(existing -> existing.getId().equals(item.getId()));
        if (!removed) {
            return this;
        }
        return new PlayerVault(next, capacityTier);
    }
}
