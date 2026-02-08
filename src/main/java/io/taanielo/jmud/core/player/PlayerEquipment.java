package io.taanielo.jmud.core.player;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.ItemId;

public class PlayerEquipment {
    private final EnumMap<EquipmentSlot, ItemId> slots;

    @JsonCreator
    public PlayerEquipment(@JsonProperty("slots") Map<EquipmentSlot, ItemId> slots) {
        EnumMap<EquipmentSlot, ItemId> next = new EnumMap<>(EquipmentSlot.class);
        if (slots != null) {
            next.putAll(slots);
        }
        this.slots = next;
    }

    public static PlayerEquipment empty() {
        return new PlayerEquipment(Map.of());
    }

    @JsonProperty("slots")
    public Map<EquipmentSlot, ItemId> slots() {
        return Map.copyOf(slots);
    }

    public ItemId equipped(EquipmentSlot slot) {
        Objects.requireNonNull(slot, "Equipment slot is required");
        return slots.get(slot);
    }

    public PlayerEquipment equip(EquipmentSlot slot, ItemId itemId) {
        Objects.requireNonNull(slot, "Equipment slot is required");
        Objects.requireNonNull(itemId, "Item id is required");
        EnumMap<EquipmentSlot, ItemId> next = new EnumMap<>(slots);
        next.put(slot, itemId);
        return new PlayerEquipment(next);
    }

    public PlayerEquipment unequip(EquipmentSlot slot) {
        Objects.requireNonNull(slot, "Equipment slot is required");
        if (!slots.containsKey(slot)) {
            return this;
        }
        EnumMap<EquipmentSlot, ItemId> next = new EnumMap<>(slots);
        next.remove(slot);
        return new PlayerEquipment(next);
    }

    public boolean isEquipped(ItemId itemId) {
        Objects.requireNonNull(itemId, "Item id is required");
        return slots.containsValue(itemId);
    }

    public EquipmentSlot equippedSlot(ItemId itemId) {
        Objects.requireNonNull(itemId, "Item id is required");
        for (Map.Entry<EquipmentSlot, ItemId> entry : slots.entrySet()) {
            if (entry.getValue().equals(itemId)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
