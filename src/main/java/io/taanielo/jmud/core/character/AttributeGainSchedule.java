package io.taanielo.jmud.core.character;

import java.util.Objects;

/**
 * Immutable, per-class schedule describing how each core attribute grows as a character levels up.
 * Each attribute has an independent {@link AttributeGainCadence} (e.g. a warrior gains strength every
 * level and agility every three levels). The schedule is fully deterministic: the total gain at a
 * given level is a pure function of the level, never a random roll (AGENTS.md §5).
 *
 * @param strength  cadence at which strength grows
 * @param intellect cadence at which intellect grows
 * @param wisdom    cadence at which wisdom grows
 * @param agility   cadence at which agility grows
 */
public record AttributeGainSchedule(
    AttributeGainCadence strength,
    AttributeGainCadence intellect,
    AttributeGainCadence wisdom,
    AttributeGainCadence agility
) {

    /** A schedule under which no attribute grows from levelling; the default for classless data. */
    public static final AttributeGainSchedule NONE =
        new AttributeGainSchedule(
            AttributeGainCadence.NONE,
            AttributeGainCadence.NONE,
            AttributeGainCadence.NONE,
            AttributeGainCadence.NONE);

    /** Normalises {@code null} cadences to {@link AttributeGainCadence#NONE}. */
    public AttributeGainSchedule {
        strength = Objects.requireNonNullElse(strength, AttributeGainCadence.NONE);
        intellect = Objects.requireNonNullElse(intellect, AttributeGainCadence.NONE);
        wisdom = Objects.requireNonNullElse(wisdom, AttributeGainCadence.NONE);
        agility = Objects.requireNonNullElse(agility, AttributeGainCadence.NONE);
    }

    /**
     * Returns the accumulated attribute gains for a character at the given level as a signed bonus.
     *
     * @param level the character's current level (1-based)
     * @return the total gains across all four attributes at that level
     */
    public AttributeBonus gainsAtLevel(int level) {
        return new AttributeBonus(
            strength.gainAtLevel(level),
            intellect.gainAtLevel(level),
            wisdom.gainAtLevel(level),
            agility.gainAtLevel(level)
        );
    }
}
