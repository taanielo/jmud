package io.taanielo.jmud.core.world;

import java.util.List;
import java.util.Objects;

import lombok.Value;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.taanielo.jmud.core.messaging.MessageSpec;

@Value
public class Item {
    ItemId id;
    String name;
    String description;
    ItemAttributes attributes;
    List<ItemEffect> effects;
    List<MessageSpec> messages;
    EquipmentSlot equipSlot;
    int weight;
    int value;

    @JsonCreator
    public Item(
        @JsonProperty("id") ItemId id,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("attributes") ItemAttributes attributes,
        @JsonProperty("effects") List<ItemEffect> effects,
        @JsonProperty("messages") List<MessageSpec> messages,
        @JsonProperty("equipSlot") EquipmentSlot equipSlot,
        @JsonProperty("weight") int weight,
        @JsonProperty("value") int value
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
        this.equipSlot = equipSlot;
        if (weight < 0) {
            throw new IllegalArgumentException("Item weight must be non-negative");
        }
        this.weight = weight;
        if (value < 0) {
            throw new IllegalArgumentException("Item value must be non-negative");
        }
        this.value = value;
    }
}
