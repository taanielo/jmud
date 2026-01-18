package io.taanielo.jmud.core.character;

import java.util.Objects;

public class Race {
    private final RaceId id;
    private final String name;
    private final int healingBaseModifier;

    public Race(RaceId id, String name, int healingBaseModifier) {
        this.id = Objects.requireNonNull(id, "Race id is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Race name must not be blank");
        }
        this.name = name;
        this.healingBaseModifier = healingBaseModifier;
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
}
