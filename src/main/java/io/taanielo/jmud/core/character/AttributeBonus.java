package io.taanielo.jmud.core.character;

/**
 * Immutable signed delta applied to a character's {@link CharacterAttributes}, used to model race
 * bonuses and a class's creation bonuses (e.g. orc {@code +3 STR -2 INT}, paladin {@code +2 STR
 * +1 WIS}). Any component may be negative.
 *
 * @param strength  signed strength delta
 * @param intellect signed intellect delta
 * @param wisdom    signed wisdom delta
 * @param agility   signed agility delta
 */
public record AttributeBonus(int strength, int intellect, int wisdom, int agility) {

    /** A bonus that adds nothing to any attribute; the default for data that omits attribute deltas. */
    public static final AttributeBonus NONE = new AttributeBonus(0, 0, 0, 0);

    /**
     * Returns the sum of this bonus and another, component-wise. Used to accumulate a race bonus, a
     * class creation bonus and the class level schedule into a single delta.
     *
     * @param other the bonus to add
     * @return the combined bonus
     */
    public AttributeBonus plus(AttributeBonus other) {
        return new AttributeBonus(
            strength + other.strength(),
            intellect + other.intellect(),
            wisdom + other.wisdom(),
            agility + other.agility()
        );
    }
}
