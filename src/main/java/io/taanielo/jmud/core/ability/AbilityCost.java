package io.taanielo.jmud.core.ability;

/**
 * The resource cost of using an ability.
 *
 * @param mana          the base mana cost paid on every use
 * @param move          the movement-point cost paid on every use
 * @param manaPerTarget additional mana charged once per target struck; used by area-of-effect
 *                      abilities ({@link AbilityTargeting#AoE}) so a crowded room costs more to
 *                      blanket. Zero for single-target abilities.
 */
public record AbilityCost(int mana, int move, int manaPerTarget) {
    public AbilityCost {
        if (mana < 0) {
            throw new IllegalArgumentException(
                "Mana cost must be non-negative"
            );
        }
        if (move < 0) {
            throw new IllegalArgumentException(
                "Move cost must be non-negative"
            );
        }
        if (manaPerTarget < 0) {
            throw new IllegalArgumentException(
                "Mana-per-target cost must be non-negative"
            );
        }
    }

    /**
     * Convenience constructor for single-target abilities with no per-target scaling.
     *
     * @param mana the base mana cost
     * @param move the movement-point cost
     */
    public AbilityCost(int mana, int move) {
        this(mana, move, 0);
    }

    /**
     * Returns the total mana required to strike {@code targetCount} targets: the base
     * {@link #mana()} cost plus {@link #manaPerTarget()} for every target hit.
     *
     * @param targetCount the number of targets the ability will affect; must be non-negative
     * @return the scaled mana cost
     */
    public int totalMana(int targetCount) {
        if (targetCount < 0) {
            throw new IllegalArgumentException("Target count must be non-negative");
        }
        return mana + manaPerTarget * targetCount;
    }
}
