package io.taanielo.jmud.core.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.world.Item;

public class PlayerInventory {
    private final List<Item> items;

    public PlayerInventory(List<Item> items) {
        this.items = List.copyOf(Objects.requireNonNullElse(items, List.of()));
    }

    public List<Item> items() {
        return items;
    }

    public PlayerInventory withItems(List<Item> nextItems) {
        return new PlayerInventory(nextItems);
    }

    public PlayerInventory addItem(Item item) {
        Objects.requireNonNull(item, "Item is required");
        List<Item> next = new ArrayList<>(items);
        next.add(item);
        return new PlayerInventory(next);
    }

    public PlayerInventory removeItem(Item item) {
        Objects.requireNonNull(item, "Item is required");
        List<Item> next = new ArrayList<>(items);
        boolean removed = next.removeIf(existing -> existing.getId().equals(item.getId()));
        if (!removed) {
            return this;
        }
        return new PlayerInventory(next);
    }
}
