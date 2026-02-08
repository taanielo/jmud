package io.taanielo.jmud.core.character;

import java.util.Objects;

public class ClassDefinition {
    private final ClassId id;
    private final String name;
    private final int healingBaseModifier;
    private final int carryBonus;

    public ClassDefinition(ClassId id, String name, int healingBaseModifier, int carryBonus) {
        this.id = Objects.requireNonNull(id, "Class id is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Class name must not be blank");
        }
        this.name = name;
        this.healingBaseModifier = healingBaseModifier;
        if (carryBonus < 0) {
            throw new IllegalArgumentException("Class carry bonus must be non-negative");
        }
        this.carryBonus = carryBonus;
    }

    public ClassId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int healingBaseModifier() {
        return healingBaseModifier;
    }

    public int carryBonus() {
        return carryBonus;
    }
}
