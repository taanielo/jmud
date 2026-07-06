package io.taanielo.jmud.core.world;

import java.util.List;
import java.util.Objects;

import lombok.Value;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.combat.AttackId;
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
    AttackId attackRef;
    AbilityId teachesAbilityRef;

    /**
     * Constructs an item. Note this class is never bound directly by Jackson: JSON persistence
     * goes through {@link io.taanielo.jmud.core.world.dto.ItemDto} and
     * {@link io.taanielo.jmud.core.world.dto.ItemMapper}, which map fields explicitly, keeping
     * this domain type free of JSON-infrastructure annotations (AGENTS.md §3.2).
     */
    public Item(
        ItemId id,
        String name,
        String description,
        ItemAttributes attributes,
        List<ItemEffect> effects,
        List<MessageSpec> messages,
        EquipmentSlot equipSlot,
        int weight,
        int value,
        AttackId attackRef,
        AbilityId teachesAbilityRef
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
        this.attackRef = attackRef;
        this.teachesAbilityRef = teachesAbilityRef;
    }

    /**
     * Convenience constructor for items that teach no ability, preserving existing call sites
     * that predate {@link #teachesAbilityRef}.
     */
    public Item(
        ItemId id,
        String name,
        String description,
        ItemAttributes attributes,
        List<ItemEffect> effects,
        List<MessageSpec> messages,
        EquipmentSlot equipSlot,
        int weight,
        int value,
        AttackId attackRef
    ) {
        this(id, name, description, attributes, effects, messages, equipSlot, weight, value, attackRef, null);
    }
}
