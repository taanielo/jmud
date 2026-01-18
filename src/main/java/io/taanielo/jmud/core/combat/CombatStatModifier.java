package io.taanielo.jmud.core.combat;

/**
 * Represents additive and multiplicative modifiers applied to a combat stat.
 */
public record CombatStatModifier(int add, int multiplier) {
    /**
     * Applies this modifier to the provided base value.
     */
    public int apply(int base) {
        long result = (long) base + add;
        result *= multiplier;
        if (result > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (result < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) result;
    }

    /**
     * Returns an empty modifier with no changes.
     */
    public static CombatStatModifier none() {
        return new CombatStatModifier(0, 1);
    }
}
