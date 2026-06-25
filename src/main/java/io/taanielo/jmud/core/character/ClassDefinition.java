package io.taanielo.jmud.core.character;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.ability.AbilityId;

/**
 * Immutable definition of a playable class, including its stat modifiers and the
 * abilities that are granted to new characters who choose this class.
 */
public class ClassDefinition {
    private final ClassId id;
    private final String name;
    private final int healingBaseModifier;
    private final int carryBonus;
    private final List<AbilityId> startingAbilityIds;

    /**
     * Constructs a class definition with starting ability ids.
     *
     * @param id                 the unique class identifier
     * @param name               the display name of the class
     * @param healingBaseModifier modifier applied to the base healing rate
     * @param carryBonus         bonus carry capacity granted by this class
     * @param startingAbilityIds ability ids granted to new players of this class
     */
    public ClassDefinition(
        ClassId id,
        String name,
        int healingBaseModifier,
        int carryBonus,
        List<AbilityId> startingAbilityIds
    ) {
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
        this.startingAbilityIds = startingAbilityIds == null
            ? List.of()
            : List.copyOf(startingAbilityIds);
    }

    /**
     * Constructs a class definition without explicit starting abilities.
     * Equivalent to passing an empty starting ability list.
     *
     * @param id                 the unique class identifier
     * @param name               the display name of the class
     * @param healingBaseModifier modifier applied to the base healing rate
     * @param carryBonus         bonus carry capacity granted by this class
     */
    public ClassDefinition(ClassId id, String name, int healingBaseModifier, int carryBonus) {
        this(id, name, healingBaseModifier, carryBonus, List.of());
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

    /**
     * Returns the list of ability ids granted to a new character who chooses this class.
     *
     * @return unmodifiable list of starting ability ids; never null
     */
    public List<AbilityId> startingAbilityIds() {
        return startingAbilityIds;
    }
}
