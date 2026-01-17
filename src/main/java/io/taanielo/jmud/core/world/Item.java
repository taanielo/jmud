package io.taanielo.jmud.core.world;

import java.util.Objects;

import lombok.Value;

@Value
public class Item {
    ItemId id;
    String name;
    String description;

    public Item(ItemId id, String name, String description) {
        this.id = Objects.requireNonNull(id, "Item id is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Item name must not be blank");
        }
        this.name = name;
        this.description = Objects.requireNonNull(description, "Item description is required");
    }
}
