package io.taanielo.jmud.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

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
     * Maximum number of items this container can hold, or {@code null} when the item is not a
     * container. A positive value marks the item as a bag/chest/strongbox; see
     * {@link #isContainer()}. Contents are packed independently of the container's own
     * {@link #weight} (items inside do not add carry weight while stored).
     */
    Integer containerCapacity;
    /**
     * Items currently held inside this container. Always empty for non-container items and for
     * empty containers. Nesting is not supported: contained items are never themselves
     * containers.
     */
    List<Item> containedItems;
    /**
     * Radius of light this item emits while carried, or {@code null} when the item is not a light
     * source. A positive value (e.g. {@code 1} for a torch, {@code 2} for a lantern) lets the
     * carrier see in dark rooms; see {@link #isLightSource()}. Consumed at read time by
     * {@link io.taanielo.jmud.core.player.LightingService}; not stored on the room.
     */
    @Nullable Integer lightRadius;

    /**
     * Constructs an item. Note this class is never bound directly by Jackson for item-definition
     * files: that persistence goes through {@link io.taanielo.jmud.core.world.dto.ItemDto} and
     * {@link io.taanielo.jmud.core.world.dto.ItemMapper}, which map fields explicitly, keeping
     * this domain type free of JSON-infrastructure annotations (AGENTS.md §3.2).
     *
     * @param containerCapacity max slots when this item is a container, or {@code null} for a
     *                          normal item; must be positive when present
     * @param containedItems    the items held inside a container; must be empty for non-containers
     *                          and never exceed {@code containerCapacity}
     * @param lightRadius       the radius of light emitted while carried, or {@code null} for a
     *                          non-light-emitting item; must be positive when present
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
        AbilityId teachesAbilityRef,
        Integer containerCapacity,
        List<Item> containedItems,
        @Nullable Integer lightRadius
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
        List<Item> contents = List.copyOf(Objects.requireNonNullElse(containedItems, List.of()));
        if (containerCapacity == null) {
            if (!contents.isEmpty()) {
                throw new IllegalArgumentException("Only container items may hold contents");
            }
        } else {
            if (containerCapacity <= 0) {
                throw new IllegalArgumentException("Container capacity must be positive");
            }
            if (contents.size() > containerCapacity) {
                throw new IllegalArgumentException("Container contents exceed capacity");
            }
            for (Item contained : contents) {
                if (contained.isContainer()) {
                    throw new IllegalArgumentException("Containers may not hold other containers");
                }
            }
        }
        this.containerCapacity = containerCapacity;
        this.containedItems = contents;
        if (lightRadius != null && lightRadius <= 0) {
            throw new IllegalArgumentException("Light radius must be positive");
        }
        this.lightRadius = lightRadius;
    }

    /**
     * Convenience constructor for items with no light-emitting radius, preserving call sites that
     * predate {@link #lightRadius}.
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
        AbilityId teachesAbilityRef,
        Integer containerCapacity,
        List<Item> containedItems
    ) {
        this(id, name, description, attributes, effects, messages, equipSlot, weight, value, attackRef,
            teachesAbilityRef, containerCapacity, containedItems, null);
    }

    /**
     * Convenience constructor for non-container items that teach an ability (or not), preserving
     * call sites that predate the container fields.
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
        this(id, name, description, attributes, effects, messages, equipSlot, weight, value, attackRef,
            teachesAbilityRef, null, List.of());
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

    /**
     * Returns whether this item is a container that can hold other items.
     */
    public boolean isContainer() {
        return containerCapacity != null;
    }

    /**
     * Returns whether this item emits light while carried (i.e. has a positive
     * {@link #lightRadius}), letting its carrier see in dark rooms.
     */
    public boolean isLightSource() {
        return lightRadius != null && lightRadius > 0;
    }

    /**
     * Returns the item's name, annotated with its fill level {@code (count/capacity)} when it is a
     * container (e.g. {@code "a leather bag (3/5)"}). Non-container items return their plain name.
     */
    public String displayName() {
        if (!isContainer()) {
            return name;
        }
        return name + " (" + containedItems.size() + "/" + containerCapacity + ")";
    }

    /**
     * Returns the number of items currently held inside this container (0 for non-containers).
     */
    public int containedItemCount() {
        return containedItems.size();
    }

    /**
     * Returns whether this container is full (always {@code false} for non-containers).
     */
    public boolean isFull() {
        return isContainer() && containedItems.size() >= containerCapacity;
    }

    /**
     * Returns a copy of this container with {@code item} added to its contents.
     *
     * @param item the non-container item to place inside
     * @return a new container instance holding the added item
     * @throws IllegalStateException    if this item is not a container or is already full
     * @throws IllegalArgumentException if {@code item} is itself a container
     */
    public Item withContainedItem(Item item) {
        Objects.requireNonNull(item, "Item is required");
        if (!isContainer()) {
            throw new IllegalStateException("This item is not a container");
        }
        if (isFull()) {
            throw new IllegalStateException("Container is full");
        }
        List<Item> next = new ArrayList<>(containedItems);
        next.add(item);
        return withContainedItems(next);
    }

    /**
     * Returns a copy of this container with the first item matching {@code itemId} removed from
     * its contents. If no contained item matches, this instance is returned unchanged.
     *
     * @param itemId the id of the contained item to remove
     * @return a new container instance without the matched item, or {@code this} if none matched
     */
    public Item withoutContainedItem(ItemId itemId) {
        Objects.requireNonNull(itemId, "Item id is required");
        List<Item> next = new ArrayList<>(containedItems);
        boolean removed = false;
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).getId().equals(itemId)) {
                next.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) {
            return this;
        }
        return withContainedItems(next);
    }

    /**
     * Returns a copy of this container with the given contents replacing the current ones.
     *
     * @param nextContents the new contents; must satisfy this container's capacity and nesting rules
     * @return a new container instance
     */
    public Item withContainedItems(List<Item> nextContents) {
        return new Item(id, name, description, attributes, effects, messages, equipSlot, weight, value,
            attackRef, teachesAbilityRef, containerCapacity, nextContents, lightRadius);
    }
}
