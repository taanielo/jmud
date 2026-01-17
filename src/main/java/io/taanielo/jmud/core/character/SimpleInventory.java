package io.taanielo.jmud.core.character;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;

public class SimpleInventory implements Inventory {

    private final List<Item> items = new ArrayList<>();

    @Override
    public void add(Item item) {
        items.add(Objects.requireNonNull(item, "Item is required"));
    }

    @Override
    public boolean remove(ItemId itemId) {
        Objects.requireNonNull(itemId, "Item id is required");
        return items.removeIf(item -> item.getId().equals(itemId));
    }

    @Override
    public List<Item> items() {
        return List.copyOf(items);
    }

    @Override
    public int size() {
        return items.size();
    }
}
