package io.taanielo.jmud.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
     * Maximum number of items this container can hold, or null when the item is not a container. A
     * positive value marks the item as a bag/chest/strongbox; see isContainer(). Contents are packed
     * independently of the container's own weight (items inside do not add carry weight while
     * stored).
     */
    Integer containerCapacity;
    /**
     * Items currently held inside this container. Always empty for non-container items and for
     * empty containers. Nesting is not supported: contained items are never themselves
     * containers.
     */
    List<Item> containedItems;
    /**
     * Radius of light this item emits while carried, or null when the item is not a light source. A
     * positive value (e.g. 1 for a torch, 2 for a lantern) lets the carrier see in dark rooms; see
     * isLightSource(). Consumed at read time by LightingService; not stored on the room.
     */
    @Nullable Integer lightRadius;
    /**
     * Maximum durability of this equippable item, or null when the item is unbreakable. A positive
     * value marks the item as breakable gear that wears down as its wearer takes damage in combat;
     * see isBreakable(). Durability logic lives in ItemDurabilityService.
     */
    @Nullable Integer maxDurability;
    /**
     * Current durability of this item, from 0 (broken, unusable in combat) up to maxDurability.
     * Always null for unbreakable items; defaults to maxDurability when a breakable item is created
     * without an explicit value.
     */
    @Nullable Integer durability;
    /**
     * Rarity tier of this item, governing its colored display name and which affixes it may carry.
     * Never null; defaults to Rarity.COMMON for items created without an explicit tier, keeping
     * legacy item data (which has no rarity field) fully backward compatible.
     */
    Rarity rarity;
    /**
     * Ids of the stat affixes attached to this item, resolved into bonus stats by ItemAffixService.
     * Always empty for plain items; the base attributes are never mutated to fold these in.
     */
    List<AffixId> affixes;
    /**
     * Whether this item's true nature — its rarity tier and affixes — is known to its holder.
     * Unidentified items display generically (e.g. "an unidentified longsword") and hide their rarity
     * coloring and affix stats until revealed by the IDENTIFY command or by reading the item; see
     * presentationName() and presentationRarity(). Defaults to true so plain items and legacy item
     * data (which has no identified field) are identified out of the box.
     */
    boolean identified;
    /**
     * Whether this container is locked, blocking access to its containedItems until it is opened
     * (e.g. by the rogue PICK skill via ContainerLockingService). Only container items may be locked;
     * defaults to false so plain items and legacy item data (which has no locked field) are unlocked
     * out of the box. See isLocked() and withLocked(boolean).
     */
    boolean locked;
    /**
     * Whether this weapon requires both hands to wield. A two-handed weapon in the WEAPON slot
     * occupies the OFFHAND slot as well: equipping it auto-unequips any off-hand item, and it blocks
     * equipping a new off-hand item (shield or second weapon) while worn. Only weapons are ever
     * two-handed; defaults to false so one-handed weapons and legacy item data (which has no
     * two_handed field) keep working exactly as before. See isTwoHanded().
     */
    boolean twoHanded;
    /**
     * Per-step move-point discount this item grants while ridden as a mount, or null when the item is
     * not a mount. A positive value marks the item as a rideable mount (a pony, a warhorse, ...) that
     * a player can MOUNT to reduce travel cost; see isMount() and mountMoveDiscount(). Only carried,
     * non-equippable mount items are ever ridden; defaults to null so ordinary items and legacy item
     * data (which has no mount_move_discount field) keep working exactly as before.
     */
    @Nullable Integer mountMoveDiscount;

    /**
     * Constructs an item from its flattened field set. This is the single, private canonical
     * constructor: all call sites build items through {@link #builder(ItemId, String, String, ItemAttributes)}
     * (which groups the optional facets into {@link ContainerState}, {@link LightSource},
     * {@link Durability}, {@link RarityProfile} and {@link Identification} value objects), while the
     * persistence layer ({@link io.taanielo.jmud.core.world.dto.ItemMapper}) maps the flat DTO fields
     * onto that builder. Keeping the flat layout here preserves the getter-based read API and keeps
     * this domain type's flat read API stable.
     *
     * <p>This constructor is also the Jackson {@link JsonCreator} used when an {@code Item} is
     * embedded directly inside another aggregate's JSON — notably a {@link
     * io.taanielo.jmud.core.player.Player Player}'s {@code inventory}/{@code equipment} and auction
     * listings, both of which round-trip the whole {@code Item} rather than a {@code data/items/*}
     * id reference. The {@link com.fasterxml.jackson.annotation.JsonProperty} names mirror the
     * Lombok getter property names so serialization and deserialization are symmetric; the
     * canonical {@link io.taanielo.jmud.core.world.dto.ItemMapper}/{@code ItemDto} path for
     * {@code data/items/*.json} is unaffected (that uses a separate DTO type). Sibling value objects
     * in this package (e.g. {@link ItemId}, {@code ItemAttributes}) already carry the same Jackson
     * annotations.
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
     * @param locked            whether this container is locked, or {@code null} to default to
     *                          {@code false} (unlocked); must be {@code null}/{@code false} for
     *                          non-container items
     * @param twoHanded         whether this weapon requires both hands, or {@code null} to default to
     *                          {@code false} (one-handed)
     * @param mountMoveDiscount the per-step move-point discount granted while ridden, or {@code null}
     *                          for a non-mount item; must be positive when present
     */
    @JsonCreator
    private Item(
        @JsonProperty("id") ItemId id,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("attributes") ItemAttributes attributes,
        @JsonProperty("effects") List<ItemEffect> effects,
        @JsonProperty("messages") List<MessageSpec> messages,
        @JsonProperty("equipSlot") EquipmentSlot equipSlot,
        @JsonProperty("weight") int weight,
        @JsonProperty("value") int value,
        @JsonProperty("attackRef") AttackId attackRef,
        @JsonProperty("teachesAbilityRef") AbilityId teachesAbilityRef,
        @JsonProperty("containerCapacity") Integer containerCapacity,
        @JsonProperty("containedItems") List<Item> containedItems,
        @JsonProperty("lightRadius") @Nullable Integer lightRadius,
        @JsonProperty("maxDurability") @Nullable Integer maxDurability,
        @JsonProperty("durability") @Nullable Integer durability,
        @JsonProperty("rarity") @Nullable Rarity rarity,
        @JsonProperty("affixes") @Nullable List<AffixId> affixes,
        @JsonProperty("identified") @Nullable Boolean identified,
        @JsonProperty("locked") @Nullable Boolean locked,
        @JsonProperty("twoHanded") @Nullable Boolean twoHanded,
        @JsonProperty("mountMoveDiscount") @Nullable Integer mountMoveDiscount
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
        boolean isLocked = locked != null && locked;
        if (isLocked && containerCapacity == null) {
            throw new IllegalArgumentException("Only container items may be locked");
        }
        this.locked = isLocked;
        this.twoHanded = twoHanded != null && twoHanded;
        if (mountMoveDiscount != null && mountMoveDiscount <= 0) {
            throw new IllegalArgumentException("Mount move discount must be positive");
        }
        this.mountMoveDiscount = mountMoveDiscount;
    }

    /**
     * Starts building an item from its required core fields. Optional facets are supplied through the
     * component records ({@link ContainerState}, {@link LightSource}, {@link Durability},
     * {@link RarityProfile}, {@link Identification}), each of which defaults to a "none"/plain value.
     * This is the single canonical construction entry point; adding a new item feature means adding a
     * field to the relevant component record and a setter here, leaving unrelated call sites untouched.
     *
     * @param id          the item's id
     * @param name        the item's display name; must not be blank
     * @param description the item's description
     * @param attributes  the item's base attributes
     * @return a new builder seeded with the required fields
     */
    public static Builder builder(ItemId id, String name, String description, ItemAttributes attributes) {
        return new Builder(id, name, description, attributes);
    }

    /**
     * Fluent builder for {@link Item}. Required fields are supplied to
     * {@link Item#builder(ItemId, String, String, ItemAttributes)}; every other facet has a sensible
     * default (empty effects/messages, no slot, zero weight/value, no container, no light, unbreakable,
     * {@link Rarity#COMMON}, identified) and is overridden only by call sites that care.
     */
    public static final class Builder {
        private final ItemId id;
        private final String name;
        private final String description;
        private final ItemAttributes attributes;
        private List<ItemEffect> effects = List.of();
        private List<MessageSpec> messages = List.of();
        @Nullable
        private EquipmentSlot equipSlot;
        private int weight;
        private int value;
        @Nullable
        private AttackId attackRef;
        @Nullable
        private AbilityId teachesAbilityRef;
        private ContainerState container = ContainerState.none();
        private LightSource light = LightSource.none();
        private Durability durability = Durability.none();
        private RarityProfile rarity = RarityProfile.common();
        private Identification identification = Identification.known();
        private boolean twoHanded;
        @Nullable
        private Integer mountMoveDiscount;

        private Builder(ItemId id, String name, String description, ItemAttributes attributes) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.attributes = attributes;
        }

        /** Sets the item's effects (defaults to none). */
        public Builder effects(List<ItemEffect> effects) {
            this.effects = effects;
            return this;
        }

        /** Sets the item's message specs (defaults to none). */
        public Builder messages(List<MessageSpec> messages) {
            this.messages = messages;
            return this;
        }

        /** Sets the item's equipment slot (defaults to none). */
        public Builder equipSlot(@Nullable EquipmentSlot equipSlot) {
            this.equipSlot = equipSlot;
            return this;
        }

        /** Sets the item's weight (defaults to 0). */
        public Builder weight(int weight) {
            this.weight = weight;
            return this;
        }

        /** Sets the item's value (defaults to 0). */
        public Builder value(int value) {
            this.value = value;
            return this;
        }

        /** Sets the attack this weapon references (defaults to none). */
        public Builder attackRef(@Nullable AttackId attackRef) {
            this.attackRef = attackRef;
            return this;
        }

        /** Sets the ability this item teaches (defaults to none). */
        public Builder teachesAbilityRef(@Nullable AbilityId teachesAbilityRef) {
            this.teachesAbilityRef = teachesAbilityRef;
            return this;
        }

        /** Sets the container facet (defaults to {@link ContainerState#none()}). */
        public Builder container(ContainerState container) {
            this.container = Objects.requireNonNull(container, "Container state is required");
            return this;
        }

        /** Sets the light-source facet (defaults to {@link LightSource#none()}). */
        public Builder light(LightSource light) {
            this.light = Objects.requireNonNull(light, "Light source is required");
            return this;
        }

        /** Sets the durability facet (defaults to {@link Durability#none()}). */
        public Builder durability(Durability durability) {
            this.durability = Objects.requireNonNull(durability, "Durability is required");
            return this;
        }

        /** Sets the rarity facet (defaults to {@link RarityProfile#common()}). */
        public Builder rarity(RarityProfile rarity) {
            this.rarity = Objects.requireNonNull(rarity, "Rarity profile is required");
            return this;
        }

        /** Sets the identification facet (defaults to {@link Identification#known()}). */
        public Builder identification(Identification identification) {
            this.identification = Objects.requireNonNull(identification, "Identification is required");
            return this;
        }

        /** Marks this weapon as requiring both hands to wield (defaults to one-handed). */
        public Builder twoHanded(boolean twoHanded) {
            this.twoHanded = twoHanded;
            return this;
        }

        /**
         * Marks this item as a rideable mount granting the given per-step move-point discount, or a
         * non-mount item when {@code null} (defaults to non-mount).
         */
        public Builder mountMoveDiscount(@Nullable Integer mountMoveDiscount) {
            this.mountMoveDiscount = mountMoveDiscount;
            return this;
        }

        /**
         * Assembles the item, validating every facet's invariants through {@link Item}'s canonical
         * constructor.
         *
         * @return the constructed item
         */
        public Item build() {
            return new Item(
                id,
                name,
                description,
                attributes,
                effects,
                messages,
                equipSlot,
                weight,
                value,
                attackRef,
                teachesAbilityRef,
                container.capacity(),
                container.contents(),
                light.radius(),
                durability.max(),
                durability.current(),
                rarity.rarity(),
                rarity.affixes(),
                identification.identified(),
                container.locked(),
                twoHanded,
                mountMoveDiscount
            );
        }
    }

    /**
     * Returns whether this item is a container that can hold other items.
     */
    @JsonIgnore
    public boolean isContainer() {
        return containerCapacity != null;
    }

    /**
     * Returns whether this item emits light while carried (i.e. has a positive
     * {@link #lightRadius}), letting its carrier see in dark rooms.
     */
    @JsonIgnore
    public boolean isLightSource() {
        return lightRadius != null && lightRadius > 0;
    }

    /**
     * Returns the item's name, annotated with its fill level {@code (count/capacity)} when it is an
     * unlocked container (e.g. {@code "a leather bag (3/5)"}) or with a {@code (locked)} suffix when
     * it is a locked container (e.g. {@code "a treasure chest (locked)"}, hiding its contents count).
     * Non-container items return their plain name.
     */
    public String displayName() {
        if (!isContainer()) {
            return name;
        }
        if (locked) {
            return name + " (locked)";
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
    @JsonIgnore
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
            rarity, affixes, identified, locked, twoHanded, mountMoveDiscount);
    }

    /**
     * Returns whether this item tracks durability (i.e. has a positive {@link #maxDurability}) and
     * can therefore wear down and break. Unbreakable items always return {@code false}.
     */
    @JsonIgnore
    public boolean isBreakable() {
        return maxDurability != null;
    }

    /**
     * Returns whether this item is broken, i.e. it is breakable and its current {@link #durability}
     * has reached {@code 0}. Broken items are unusable in combat until repaired.
     */
    @JsonIgnore
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
            rarity, affixes, identified, locked, twoHanded, mountMoveDiscount);
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
            rarity, affixes, newIdentified, locked, twoHanded, mountMoveDiscount);
    }

    /**
     * Returns a copy of this container with its locked state set to {@code newLocked}. A locked
     * container blocks access to its contents until it is opened (e.g. via the rogue PICK skill).
     * All other state — durability, contents, rarity, identification — is preserved. Returns
     * {@code this} unchanged when the state already matches.
     *
     * @param newLocked whether the copy should be locked
     * @return a new item instance with the updated locked state, or {@code this} if unchanged
     * @throws IllegalStateException if this item is not a container
     */
    public Item withLocked(boolean newLocked) {
        if (!isContainer()) {
            throw new IllegalStateException("This item is not a container");
        }
        if (this.locked == newLocked) {
            return this;
        }
        return new Item(id, name, description, attributes, effects, messages, equipSlot, weight, value,
            attackRef, teachesAbilityRef, containerCapacity, containedItems, lightRadius, maxDurability, durability,
            rarity, affixes, identified, newLocked, twoHanded, mountMoveDiscount);
    }

    /**
     * Returns a copy of this item with {@code affixId} appended to its {@link #affixes}, permanently
     * imbuing the specific instance with an additional stat affix (as produced by the ENCHANT
     * command). All other state — durability, container contents, rarity, identification — is
     * preserved. The base {@link #attributes} are never folded in; the new affix contributes its
     * bonus through {@link io.taanielo.jmud.core.world.ItemAffixService} exactly like loot-rolled
     * affixes.
     *
     * @param affixId the id of the affix to attach
     * @return a new item instance carrying the additional affix
     */
    public Item withAddedAffix(AffixId affixId) {
        Objects.requireNonNull(affixId, "Affix id is required");
        List<AffixId> nextAffixes = new ArrayList<>(affixes);
        nextAffixes.add(affixId);
        return new Item(id, name, description, attributes, effects, messages, equipSlot, weight, value,
            attackRef, teachesAbilityRef, containerCapacity, containedItems, lightRadius, maxDurability, durability,
            rarity, nextAffixes, identified, locked, twoHanded, mountMoveDiscount);
    }

    /**
     * Returns whether this item can be equipped into an {@link EquipmentSlot} (i.e. it is weapon or
     * armor rather than a consumable, quest item or plain trophy). Only equippable items may be
     * enchanted.
     *
     * @return {@code true} when the item has an equipment slot
     */
    @JsonIgnore
    public boolean isEquippable() {
        return equipSlot != null;
    }

    /**
     * Returns whether this item is a rideable mount (i.e. it carries a positive
     * {@link #mountMoveDiscount}). A player may MOUNT such an item from their inventory to reduce
     * their per-step travel cost.
     *
     * @return {@code true} when the item is a mount
     */
    @JsonIgnore
    public boolean isMount() {
        return mountMoveDiscount != null && mountMoveDiscount > 0;
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
     * Returns this item's name annotated with {@code (damaged)} when it is broken and
     * {@code (two-handed)} when it is a two-handed weapon, so heavy gear reads distinctly in
     * inventory, equipment and room listings (e.g. {@code "a greataxe (two-handed)"}). Non-broken,
     * one-handed items return their plain {@link #displayName()}.
     */
    public String durabilityDisplayName() {
        String base = displayName();
        if (isBroken()) {
            base = base + " (damaged)";
        }
        if (twoHanded) {
            base = base + " (two-handed)";
        }
        return base;
    }
}
