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
    private final String description;
    private final LevelGains levelGains;
    private final AttributeBonus attributeBonus;
    private final AttributeGainSchedule attributeGains;

    /**
     * Constructs a class definition with an armour bonus, starting ability ids,
     * trainable ability ids and a creation description, using no attribute bonuses or gains.
     *
     * @param id                  the unique class identifier
     * @param name                the display name of the class
     * @param healingBaseModifier modifier applied to the base healing rate
     * @param carryBonus          bonus carry capacity granted by this class
     * @param armorBonus          natural armour class granted by the class's armour proficiency
     * @param startingAbilityIds  ability ids granted to new players of this class
     * @param trainableAbilityIds ability ids the Master Trainer can teach members of this class
     *                            (not granted automatically at creation)
     * @param description         short flavour text shown at character creation; {@code null}
     *                            is normalised to an empty string
     * @param levelGains          per-level vitals growth for this class; {@code null} is normalised
     *                            to {@link LevelGains#DEFAULT}
     */
    public ClassDefinition(
        ClassId id,
        String name,
        int healingBaseModifier,
        int carryBonus,
        int armorBonus,
        List<AbilityId> startingAbilityIds,
        List<AbilityId> trainableAbilityIds,
        String description,
        LevelGains levelGains
    ) {
        this(id, name, healingBaseModifier, carryBonus, armorBonus, startingAbilityIds,
            trainableAbilityIds, description, levelGains, AttributeBonus.NONE, AttributeGainSchedule.NONE);
    }

    /**
     * Constructs a class definition with an armour bonus, starting ability ids, trainable ability
     * ids, a creation description, per-level vitals growth, creation attribute bonuses and a
     * per-level attribute gain schedule.
     *
     * @param id                  the unique class identifier
     * @param name                the display name of the class
     * @param healingBaseModifier modifier applied to the base healing rate
     * @param carryBonus          bonus carry capacity granted by this class
     * @param armorBonus          natural armour class granted by the class's armour proficiency
     * @param startingAbilityIds  ability ids granted to new players of this class
     * @param trainableAbilityIds ability ids the Master Trainer can teach members of this class
     *                            (not granted automatically at creation)
     * @param description         short flavour text shown at character creation; {@code null}
     *                            is normalised to an empty string
     * @param levelGains          per-level vitals growth for this class; {@code null} is normalised
     *                            to {@link LevelGains#DEFAULT}
     * @param attributeBonus      signed core-attribute bonuses applied at character creation;
     *                            {@code null} is normalised to {@link AttributeBonus#NONE}
     * @param attributeGains      per-level core-attribute growth schedule; {@code null} is normalised
     *                            to {@link AttributeGainSchedule#NONE}
     */
    public ClassDefinition(
        ClassId id,
        String name,
        int healingBaseModifier,
        int carryBonus,
        int armorBonus,
        List<AbilityId> startingAbilityIds,
        List<AbilityId> trainableAbilityIds,
        String description,
        LevelGains levelGains,
        AttributeBonus attributeBonus,
        AttributeGainSchedule attributeGains
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
        this.description = description == null ? "" : description;
        this.levelGains = levelGains == null ? LevelGains.DEFAULT : levelGains;
        this.attributeBonus = attributeBonus == null ? AttributeBonus.NONE : attributeBonus;
        this.attributeGains = attributeGains == null ? AttributeGainSchedule.NONE : attributeGains;
    }

    /**
     * Constructs a class definition with an armour bonus, starting ability ids, trainable ability
     * ids and a creation description, using the default per-level gains.
     *
     * @param id                  the unique class identifier
     * @param name                the display name of the class
     * @param healingBaseModifier modifier applied to the base healing rate
     * @param carryBonus          bonus carry capacity granted by this class
     * @param armorBonus          natural armour class granted by the class's armour proficiency
     * @param startingAbilityIds  ability ids granted to new players of this class
     * @param trainableAbilityIds ability ids the Master Trainer can teach members of this class
     * @param description         short flavour text shown at character creation
     */
    public ClassDefinition(
        ClassId id,
        String name,
        int healingBaseModifier,
        int carryBonus,
        int armorBonus,
        List<AbilityId> startingAbilityIds,
        List<AbilityId> trainableAbilityIds,
        String description
    ) {
        this(id, name, healingBaseModifier, carryBonus, armorBonus,
            startingAbilityIds, trainableAbilityIds, description, LevelGains.DEFAULT);
    }

    /**
     * Constructs a class definition with an armour bonus, starting ability ids and
     * trainable ability ids, with no creation description.
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
        this(id, name, healingBaseModifier, carryBonus, armorBonus,
            startingAbilityIds, trainableAbilityIds, "");
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

    /**
     * Returns the short flavour description shown at character creation, covering the class's
     * playstyle, benefits and signature abilities. May be an empty string for legacy data that
     * predates the description field, but never {@code null}.
     *
     * @return the creation description; never {@code null}
     */
    public String description() {
        return description;
    }

    /**
     * Returns the per-level vitals growth granted to a character of this class on level-up.
     *
     * <p>Classes lean this growth toward their archetype (HP for front-liners, mana for casters)
     * while keeping the total roughly comparable. Defaults to {@link LevelGains#DEFAULT} for
     * legacy data that predates the {@code level_gains} field.
     *
     * @return the per-level gains; never {@code null}
     */
    public LevelGains levelGains() {
        return levelGains;
    }

    /**
     * Returns the signed core-attribute bonuses granted to a new character of this class at creation
     * (e.g. mage {@code +3 INT}, paladin {@code +2 STR +1 WIS}). Defaults to
     * {@link AttributeBonus#NONE} for legacy data that predates the attributes fields.
     *
     * @return the creation attribute bonus; never {@code null}
     */
    public AttributeBonus attributeBonus() {
        return attributeBonus;
    }

    /**
     * Returns the deterministic per-level schedule by which this class grows its core attributes as
     * characters level up (e.g. warrior strength every level, agility every three levels). Defaults
     * to {@link AttributeGainSchedule#NONE} for legacy data that predates the attributes fields.
     *
     * @return the per-level attribute gain schedule; never {@code null}
     */
    public AttributeGainSchedule attributeGains() {
        return attributeGains;
    }
}
