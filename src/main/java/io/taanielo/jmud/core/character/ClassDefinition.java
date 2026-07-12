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
    private final int armorBonus;
    private final List<AbilityId> startingAbilityIds;
    private final List<AbilityId> trainableAbilityIds;

    /**
     * Constructs a class definition with an armour bonus, starting ability ids and
     * trainable ability ids.
     *
     * @param id                  the unique class identifier
     * @param name                the display name of the class
     * @param healingBaseModifier modifier applied to the base healing rate
     * @param carryBonus          bonus carry capacity granted by this class
     * @param armorBonus          natural armour class granted by the class's armour proficiency
     * @param startingAbilityIds  ability ids granted to new players of this class
     * @param trainableAbilityIds ability ids the Master Trainer can teach members of this class
     *                            (not granted automatically at creation)
     */
    public ClassDefinition(
        ClassId id,
        String name,
        int healingBaseModifier,
        int carryBonus,
        int armorBonus,
        List<AbilityId> startingAbilityIds,
        List<AbilityId> trainableAbilityIds
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
        if (armorBonus < 0) {
            throw new IllegalArgumentException("Class armor bonus must be non-negative");
        }
        this.armorBonus = armorBonus;
        this.startingAbilityIds = startingAbilityIds == null
            ? List.of()
            : List.copyOf(startingAbilityIds);
        this.trainableAbilityIds = trainableAbilityIds == null
            ? List.of()
            : List.copyOf(trainableAbilityIds);
    }

    /**
     * Constructs a class definition with an armour bonus and starting ability ids
     * and no trainable abilities.
     *
     * @param id                 the unique class identifier
     * @param name               the display name of the class
     * @param healingBaseModifier modifier applied to the base healing rate
     * @param carryBonus         bonus carry capacity granted by this class
     * @param armorBonus         natural armour class granted by the class's armour proficiency
     * @param startingAbilityIds ability ids granted to new players of this class
     */
    public ClassDefinition(
        ClassId id,
        String name,
        int healingBaseModifier,
        int carryBonus,
        int armorBonus,
        List<AbilityId> startingAbilityIds
    ) {
        this(id, name, healingBaseModifier, carryBonus, armorBonus, startingAbilityIds, List.of());
    }

    /**
     * Constructs a class definition with starting ability ids and no armour bonus.
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
        this(id, name, healingBaseModifier, carryBonus, 0, startingAbilityIds);
    }

    /**
     * Constructs a class definition without explicit starting abilities or armour bonus.
     * Equivalent to passing an empty starting ability list.
     *
     * @param id                 the unique class identifier
     * @param name               the display name of the class
     * @param healingBaseModifier modifier applied to the base healing rate
     * @param carryBonus         bonus carry capacity granted by this class
     */
    public ClassDefinition(ClassId id, String name, int healingBaseModifier, int carryBonus) {
        this(id, name, healingBaseModifier, carryBonus, 0, List.of());
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
     * Returns the natural armour class granted by this class's armour proficiency.
     * Added to the character's total AC alongside racial and equipment bonuses.
     *
     * @return the non-negative class armour bonus
     */
    public int armorBonus() {
        return armorBonus;
    }

    /**
     * Returns the list of ability ids granted to a new character who chooses this class.
     *
     * @return unmodifiable list of starting ability ids; never null
     */
    public List<AbilityId> startingAbilityIds() {
        return startingAbilityIds;
    }

    /**
     * Returns the ability ids that the Master Trainer can teach a member of this class.
     *
     * <p>These abilities are <em>not</em> granted automatically at character creation; a
     * player learns them by spending practice points at the trainer (see {@code TRAIN}).
     * This is the advanced kit that gives a new character something to train on day one.
     *
     * @return unmodifiable list of trainable ability ids; never null
     */
    public List<AbilityId> trainableAbilityIds() {
        return trainableAbilityIds;
    }
}
