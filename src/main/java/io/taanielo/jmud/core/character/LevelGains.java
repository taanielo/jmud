package io.taanielo.jmud.core.character;

/**
 * Immutable per-level vitals growth granted to a character of a given class when it levels up.
 *
 * <p>Each level-up adds {@link #hp()} to max HP, {@link #mana()} to max mana and {@link #move()}
 * to max move. Different classes lean their growth toward different vitals (a warrior gains more
 * HP than mana; a mage the reverse) while keeping the total roughly comparable, so class identity
 * shows in a character's vitals profile over time.
 *
 * @param hp   permanent max-HP gained per level-up; must be non-negative
 * @param mana permanent max-mana gained per level-up; must be non-negative
 * @param move permanent max-move gained per level-up; must be non-negative
 */
public record LevelGains(int hp, int mana, int move) {

    /**
     * Legacy flat gains applied before classes had differentiated growth, and the fallback used
     * for any class whose JSON omits the {@code level_gains} field. Kept so existing saves and
     * fieldless class data level up exactly as they did previously.
     */
    public static final LevelGains DEFAULT = new LevelGains(10, 5, 3);

    /**
     * Validates that no component is negative.
     */
    public LevelGains {
        if (hp < 0) {
            throw new IllegalArgumentException("Level HP gain must be non-negative");
        }
        if (mana < 0) {
            throw new IllegalArgumentException("Level mana gain must be non-negative");
        }
        if (move < 0) {
            throw new IllegalArgumentException("Level move gain must be non-negative");
        }
    }
}
