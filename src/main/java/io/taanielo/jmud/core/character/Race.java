package io.taanielo.jmud.core.character;

import java.util.Objects;

public class Race {
    private final RaceId id;
    private final String name;
    private final int healingBaseModifier;
    private final int carryBase;

    public Race(RaceId id, String name, int healingBaseModifier, int carryBase) {
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
}
