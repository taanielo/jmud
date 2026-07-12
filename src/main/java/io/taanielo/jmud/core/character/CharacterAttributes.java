package io.taanielo.jmud.core.character;

/**
 * Immutable set of a character's four core attributes: strength, intellect, wisdom and agility.
 *
 * <p>The baseline for every attribute is {@value #BASELINE}. The value that actually feeds combat
 * and ability formulas is the <em>modifier</em>, i.e. {@code value - BASELINE}, which may be
 * negative when a race or class penalty drags an attribute below the baseline. Strength drives
 * melee/ranged damage, intellect empowers harmful spells, wisdom empowers healing and mana regen,
 * and agility governs accuracy, dodge and critical chance.
 *
 * <p>Attributes are always <em>derived</em> deterministically from a character's race, class and
 * level (see {@link CharacterAttributesResolver}); they are never rolled and never persisted, so a
 * given race/class/level combination always yields the same attributes.
 *
 * @param strength  raw strength score; baseline {@value #BASELINE}
 * @param intellect raw intellect score; baseline {@value #BASELINE}
 * @param wisdom    raw wisdom score; baseline {@value #BASELINE}
 * @param agility   raw agility score; baseline {@value #BASELINE}
 */
public record CharacterAttributes(int strength, int intellect, int wisdom, int agility) {

    /** The baseline value of every attribute, at which its modifier is zero. */
    public static final int BASELINE = 10;

    /**
     * Returns the all-baseline attribute set (every attribute at {@value #BASELINE}), which produces
     * a zero modifier everywhere and therefore leaves combat and ability results unchanged.
     *
     * @return an attribute set with every attribute at the baseline
     */
    public static CharacterAttributes baseline() {
        return new CharacterAttributes(BASELINE, BASELINE, BASELINE, BASELINE);
    }

    /** Returns the strength modifier ({@code strength - BASELINE}); may be negative. */
    public int strengthModifier() {
        return strength - BASELINE;
    }

    /** Returns the intellect modifier ({@code intellect - BASELINE}); may be negative. */
    public int intellectModifier() {
        return intellect - BASELINE;
    }

    /** Returns the wisdom modifier ({@code wisdom - BASELINE}); may be negative. */
    public int wisdomModifier() {
        return wisdom - BASELINE;
    }

    /** Returns the agility modifier ({@code agility - BASELINE}); may be negative. */
    public int agilityModifier() {
        return agility - BASELINE;
    }

    /**
     * Returns a new attribute set with the given signed bonus applied component-wise.
     *
     * @param bonus the signed per-attribute deltas to add
     * @return the resulting attribute set
     */
    public CharacterAttributes plus(AttributeBonus bonus) {
        return new CharacterAttributes(
            strength + bonus.strength(),
            intellect + bonus.intellect(),
            wisdom + bonus.wisdom(),
            agility + bonus.agility()
        );
    }
}
