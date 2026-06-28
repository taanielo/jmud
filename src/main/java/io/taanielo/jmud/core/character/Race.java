package io.taanielo.jmud.core.character;

import java.util.Objects;

public class Race {
    private final RaceId id;
    private final String name;
    private final int healingBaseModifier;
    private final int carryBase;
    private final int armorBonus;

    public Race(RaceId id, String name, int healingBaseModifier, int carryBase) {
        this(id, name, healingBaseModifier, carryBase, 0);
    }

    public Race(RaceId id, String name, int healingBaseModifier, int carryBase, int armorBonus) {
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
}
