package io.taanielo.jmud.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
     * Maximum durability of this equippable item, or {@code null} when the item is unbreakable.
     * A positive value marks the item as breakable gear that wears down as its wearer takes damage
     * in combat; see {@link #isBreakable()}. Durability logic lives in
     * {@link io.taanielo.jmud.core.world.ItemDurabilityService}.
     */
    @Nullable Integer maxDurability;
    /**
     * Current durability of this item, from {@code 0} (broken, unusable in combat) up to
     * {@link #maxDurability}. Always {@code null} for unbreakable items; defaults to
     * {@link #maxDurability} when a breakable item is created without an explicit value.
     */
    @Nullable Integer durability;
    /**
     * Rarity tier of this item, governing its colored display name and which affixes it may carry.
     * Never {@code null}; defaults to {@link Rarity#COMMON} for items created without an explicit
     * tier, keeping legacy item data (which has no {@code rarity} field) fully backward compatible.
     */
    Rarity rarity;
    /**
     * Ids of the stat affixes attached to this item, resolved into bonus stats by
     * {@link io.taanielo.jmud.core.world.ItemAffixService}. Always empty for plain items; the base
     * {@link #attributes} are never mutated to fold these in.
     */
    List<AffixId> affixes;
    /**
     * Whether this item's true nature — its {@link #rarity} tier and {@link #affixes} — is known to
     * its holder. Unidentified items display generically (e.g. {@code "an unidentified longsword"})
     * and hide their rarity coloring and affix stats until revealed by the IDENTIFY command or by
     * reading the item; see {@link #presentationName()} and {@link #presentationRarity()}. Defaults
     * to {@code true} so plain items and legacy item data (which has no {@code identified} field)
     * are identified out of the box.
     */
    boolean identified;

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
     * @param maxDurability     the maximum durability, or {@code null} for an unbreakable item;
     *                          must be positive when present
     * @param durability        the current durability, or {@code null} to default to
     *                          {@code maxDurability}; must satisfy {@code 0 <= durability <= maxDurability}
     *                          and must be {@code null} when {@code maxDurability} is {@code null}
     * @param rarity            the rarity tier, or {@code null} to default to {@link Rarity#COMMON}
     * @param affixes           the ids of stat affixes attached to this item, or {@code null} for none
     * @param identified        whether the item's rarity and affixes are revealed, or {@code null} to
     *                          default to {@code true} (identified) for backward compatibility
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
        @Nullable Integer lightRadius,
        @Nullable Integer maxDurability,
        @Nullable Integer durability,
        @Nullable Rarity rarity,
        @Nullable List<AffixId> affixes,
        @Nullable Boolean identified
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
        if (maxDurability == null) {
            if (durability != null) {
                throw new IllegalArgumentException("Only breakable items may track durability");
            }
            this.maxDurability = null;
            this.durability = null;
        } else {
            if (maxDurability <= 0) {
                throw new IllegalArgumentException("Max durability must be positive");
            }
            int current = durability == null ? maxDurability : durability;
            if (current < 0 || current > maxDurability) {
                throw new IllegalArgumentException("Durability must be between 0 and max durability");
            }
            this.maxDurability = maxDurability;
            this.durability = current;
        }
        this.rarity = Objects.requireNonNullElse(rarity, Rarity.COMMON);
        this.affixes = List.copyOf(Objects.requireNonNullElse(affixes, List.of()));
        this.identified = identified == null || identified;
    }

    /**
     * Convenience constructor for rarity-aware call sites that predate {@link #identified}: builds an
     * item with a rarity tier and affixes that is fully identified (its true nature already known),
     * preserving call sites created before unidentified items existed.
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
        @Nullable Integer lightRadius,
        @Nullable Integer maxDurability,
        @Nullable Integer durability,
        @Nullable Rarity rarity,
        @Nullable List<AffixId> affixes
    ) {
        this(id, name, description, attributes, effects, messages, equipSlot, weight, value, attackRef,
            teachesAbilityRef, containerCapacity, containedItems, lightRadius, maxDurability, durability,
            rarity, affixes, null);
    }

    /**
     * Convenience constructor for durability-aware call sites that predate {@link #rarity} and
     * {@link #affixes}: builds an item with an optional light radius and durability tracking but no
     * rarity tier or affixes (i.e. a {@link Rarity#COMMON} item).
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
        @Nullable Integer lightRadius,
        @Nullable Integer maxDurability,
        @Nullable Integer durability
    ) {
        this(id, name, description, attributes, effects, messages, equipSlot, weight, value, attackRef,
            teachesAbilityRef, containerCapacity, containedItems, lightRadius, maxDurability, durability,
            null, null, null);
    }

    /**
     * Convenience constructor for breakable-agnostic call sites: builds an item with an optional
     * light radius but no durability tracking, preserving call sites that predate
     * {@link #maxDurability}.
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
        this(id, name, description, attributes, effects, messages, equipSlot, weight, value, attackRef,
            teachesAbilityRef, containerCapacity, containedItems, lightRadius, null, null, null, null);
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
            teachesAbilityRef, containerCapacity, containedItems, null, null, null, null, null);
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
            attackRef, teachesAbilityRef, containerCapacity, nextContents, lightRadius, maxDurability, durability,
            rarity, affixes, identified);
    }

    /**
     * Returns whether this item tracks durability (i.e. has a positive {@link #maxDurability}) and
     * can therefore wear down and break. Unbreakable items always return {@code false}.
     */
    public boolean isBreakable() {
        return maxDurability != null;
    }

    /**
     * Returns whether this item is broken, i.e. it is breakable and its current {@link #durability}
     * has reached {@code 0}. Broken items are unusable in combat until repaired.
     */
    public boolean isBroken() {
        return maxDurability != null && durability != null && durability <= 0;
    }

    /**
     * Returns a copy of this item with its current durability set to the given value, clamped to
     * the range {@code [0, maxDurability]}.
     *
     * @param newDurability the desired durability
     * @return a new item instance with the updated durability
     * @throws IllegalStateException if this item is not breakable
     */
    public Item withDurability(int newDurability) {
        if (maxDurability == null) {
            throw new IllegalStateException("This item is not breakable");
        }
        int clamped = Math.max(0, Math.min(maxDurability, newDurability));
        return new Item(id, name, description, attributes, effects, messages, equipSlot, weight, value,
            attackRef, teachesAbilityRef, containerCapacity, containedItems, lightRadius, maxDurability, clamped,
            rarity, affixes, identified);
    }

    /**
     * Returns a copy of this item with its identification status set to {@code newIdentified},
     * revealing (or re-hiding) its {@link #rarity} tier and {@link #affixes} for display. All other
     * state — including durability and container contents — is preserved. Returns {@code this}
     * unchanged when the status already matches.
     *
     * @param newIdentified whether the copy should be identified
     * @return a new item instance with the updated identification status, or {@code this} if unchanged
     */
    public Item withIdentified(boolean newIdentified) {
        if (this.identified == newIdentified) {
            return this;
        }
        return new Item(id, name, description, attributes, effects, messages, equipSlot, weight, value,
            attackRef, teachesAbilityRef, containerCapacity, containedItems, lightRadius, maxDurability, durability,
            rarity, affixes, newIdentified);
    }

    /**
     * Returns the name to display for this item to a holder, hiding its true identity while it is
     * unidentified. Identified items return their full {@link #durabilityDisplayName()} (composing
     * container fill and {@code (damaged)} annotations); unidentified items return a generic
     * {@code "an unidentified <noun>"} label that reveals neither rarity nor affixes.
     *
     * @return the holder-facing display name
     */
    public String presentationName() {
        if (identified) {
            return durabilityDisplayName();
        }
        return "an unidentified " + genericNoun();
    }

    /**
     * Returns the rarity tier to use when coloring this item's {@link #presentationName()}. Identified
     * items expose their true {@link #rarity}; unidentified items report {@link Rarity#COMMON} so that
     * no rarity coloring leaks their true tier before identification.
     *
     * @return the rarity tier to display
     */
    public Rarity presentationRarity() {
        return identified ? rarity : Rarity.COMMON;
    }

    /**
     * Strips any leading indefinite/definite article from the item's {@link #name} to yield a bare
     * noun phrase (e.g. {@code "a longsword"} to {@code "longsword"}) for the generic unidentified
     * label. Names without a recognised leading article are returned unchanged.
     *
     * @return the article-stripped noun phrase
     */
    private String genericNoun() {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String article : new String[] {"a ", "an ", "the "}) {
            if (lower.startsWith(article)) {
                return name.substring(article.length());
            }
        }
        return name;
    }

    /**
     * Returns this item's name annotated with {@code (damaged)} when it is broken, so broken gear
     * reads distinctly in inventory, equipment and room listings. Non-broken items return their
     * plain {@link #displayName()}.
     */
    public String durabilityDisplayName() {
        if (isBroken()) {
            return displayName() + " (damaged)";
        }
        return displayName();
    }
}
