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
}
