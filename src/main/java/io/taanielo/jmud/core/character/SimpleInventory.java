package io.taanielo.jmud.core.character;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SimpleInventory implements Inventory {

    private final List<String> items;

    public SimpleInventory() {
        this.items = List.of();
    }

    private SimpleInventory(List<String> items) {
        this.items = List.copyOf(items);
    }

    @Override
    public Inventory add(String itemId) {
        validateItemId(itemId);
        List<String> next = new ArrayList<>(items);
        next.add(itemId);
        return new SimpleInventory(next);
    }

    @Override
    public Inventory remove(String itemId) {
        validateItemId(itemId);
        List<String> next = new ArrayList<>(items);
        next.removeIf(itemId::equals);
        return new SimpleInventory(next);
    }

    @Override
    public List<String> items() {
        return List.copyOf(items);
    }

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean contains(String itemId) {
        validateItemId(itemId);
        return items.contains(itemId);
    }

    private void validateItemId(String itemId) {
        Objects.requireNonNull(itemId, "Item id is required");
        if (itemId.isBlank()) {
            throw new IllegalArgumentException("Item id must not be blank");
        }
    }
}
