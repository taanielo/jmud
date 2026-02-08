package io.taanielo.jmud.core.world;

import java.util.List;
import java.util.Objects;

import lombok.Value;

import io.taanielo.jmud.core.messaging.MessageSpec;

@Value
public class Item {
    ItemId id;
    String name;
    String description;
    ItemAttributes attributes;
    List<ItemEffect> effects;
    List<MessageSpec> messages;
    int value;

    public Item(
        ItemId id,
        String name,
        String description,
        ItemAttributes attributes,
        List<ItemEffect> effects,
        List<MessageSpec> messages,
        int value
    ) {
        this.id = Objects.requireNonNull(id, "Item id is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Item name must not be blank");
        }
        this.name = name;
        this.description = Objects.requireNonNull(description, "Item description is required");
        this.attributes = Objects.requireNonNull(attributes, "Item attributes are required");
        this.effects = List.copyOf(Objects.requireNonNull(effects, "Item effects are required"));
        this.messages = List.copyOf(Objects.requireNonNullElse(messages, List.of()));
        if (value < 0) {
            throw new IllegalArgumentException("Item value must be non-negative");
        }
        this.value = value;
    }
}
