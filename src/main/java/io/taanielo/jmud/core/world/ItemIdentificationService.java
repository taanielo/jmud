package io.taanielo.jmud.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Domain service governing item identification: turning an unidentified item — whose true rarity
 * tier and stat affixes are hidden from its holder — into an identified one that displays its full
 * colored name and affixes.
 *
 * <p>All operations are pure functions over immutable {@link Item} values; nothing is mutated in
 * place. Callers on the tick thread (AGENTS.md §5) apply the returned copies. The service is
 * stateless, so a single instance is safe to share.
 *
 * <p>An "identify scroll" — a consumable read to reveal an item — is any unidentified item with a
 * read action: reading it identifies the item itself (see {@code GameActionService.readItem}). This
 * service treats identification uniformly for every item type and does not special-case scrolls.
 */
public class ItemIdentificationService {

    /**
     * Marks the given item as identified, revealing its rarity tier and affixes for display. An
     * already-identified item is returned unchanged.
     *
     * @param item the item to identify; must not be null
     * @return the identified item (a copy), or the same instance when it was already identified
     */
    public Item identify(Item item) {
        Objects.requireNonNull(item, "Item is required");
        return item.withIdentified(true);
    }

    /**
     * Returns a copy of {@code inventory} with the first item equal to {@code target} replaced by its
     * identified copy, preserving list order. When no element equals {@code target}, the original
     * list is returned unchanged.
     *
     * @param inventory the current inventory list; must not be null
     * @param target    the item to identify in place; must not be null
     * @return the inventory with {@code target} identified, or the original when it is absent
     */
    public List<Item> identifyInInventory(List<Item> inventory, Item target) {
        Objects.requireNonNull(inventory, "Inventory is required");
        Objects.requireNonNull(target, "Target item is required");
        List<Item> next = new ArrayList<>(inventory.size());
        boolean replaced = false;
        for (Item item : inventory) {
            if (!replaced && item.equals(target)) {
                next.add(identify(item));
                replaced = true;
            } else {
                next.add(item);
            }
        }
        if (!replaced) {
            return inventory;
        }
        return next;
    }
}
