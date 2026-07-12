package io.taanielo.jmud.core.character;

import java.util.Objects;

public class Race {
    private final RaceId id;
    private final String name;
    private final int healingBaseModifier;
    private final int carryBase;
    private final int armorBonus;
    private final int manaModifier;
    private final int attackModifier;
    private final String description;
    private final AttributeBonus attributeBonus;

    public Race(RaceId id, String name, int healingBaseModifier, int carryBase) {
        this(id, name, healingBaseModifier, carryBase, 0);
    }

    public Race(RaceId id, String name, int healingBaseModifier, int carryBase, int armorBonus) {
        this(id, name, healingBaseModifier, carryBase, armorBonus, 0, 0);
    }

    public Race(
        RaceId id,
        String name,
        int healingBaseModifier,
        int carryBase,
        int armorBonus,
        int manaModifier,
        int attackModifier
    ) {
        this(id, name, healingBaseModifier, carryBase, armorBonus, manaModifier, attackModifier, "");
    }

    public Race(
        RaceId id,
        String name,
        int healingBaseModifier,
        int carryBase,
        int armorBonus,
        int manaModifier,
        int attackModifier,
        String description
    ) {
        this(id, name, healingBaseModifier, carryBase, armorBonus, manaModifier, attackModifier,
            description, AttributeBonus.NONE);
    }

    public Race(
        RaceId id,
        String name,
        int healingBaseModifier,
        int carryBase,
        int armorBonus,
        int manaModifier,
        int attackModifier,
        String description,
        AttributeBonus attributeBonus
    ) {
        this.id = Objects.requireNonNull(id, "Race id is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Race name must not be blank");
        }
        this.name = name;
        this.healingBaseModifier = healingBaseModifier;
        if (carryBase < 0) {
            throw new IllegalArgumentException("Race carry base must be non-negative");
        }
        this.carryBase = carryBase;
        if (armorBonus < 0) {
            throw new IllegalArgumentException("Race armor bonus must be non-negative");
        }
        this.armorBonus = armorBonus;
        this.manaModifier = manaModifier;
        this.attackModifier = attackModifier;
        this.description = description == null ? "" : description;
        this.attributeBonus = attributeBonus == null ? AttributeBonus.NONE : attributeBonus;
    }

    public RaceId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int healingBaseModifier() {
        return healingBaseModifier;
    }

    public int carryBase() {
        return carryBase;
    }

    /** Returns the natural armor bonus that reduces attacker hit chance in combat. */
    public int armorBonus() {
        return armorBonus;
    }

    /**
     * Returns the signed adjustment applied to a new character's maximum mana at creation.
     * Positive values deepen the mana pool (e.g. Elf); negative values shrink it (e.g. Orc).
     */
    public int manaModifier() {
        return manaModifier;
    }

    /**
     * Returns the signed adjustment applied to the character's combat hit chance,
     * representing physical prowess. Positive values improve landing attacks (e.g. Orc).
     */
    public int attackModifier() {
        return attackModifier;
    }

    /**
     * Returns the short flavour description shown at character creation, covering the race's
     * playstyle, benefits and signature traits. May be an empty string for legacy data that
     * predates the description field, but never {@code null}.
     */
    public String description() {
        return description;
    }

    /**
     * Returns the signed core-attribute bonus granted by this race (e.g. orc {@code +3 STR -2 INT}).
     * Defaults to {@link AttributeBonus#NONE} for legacy data that predates the attributes field.
     *
     * @return the race's attribute bonus; never {@code null}
     */
    public AttributeBonus attributeBonus() {
        return attributeBonus;
    }
}
