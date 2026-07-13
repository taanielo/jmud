package io.taanielo.jmud.core.world.area;

/**
 * The recommended character-level band for an {@link Area}, giving players an out-of-combat signal
 * for whether a zone suits their level before they travel there (issue #550).
 *
 * <p>The band is authored from the area's actual content — the toughness of the mobs that spawn in
 * its rooms and any {@code recommended_level} on quests targeting it — so it is honest, not guessed.
 * It carries no runtime state and never gates entry; it is purely advisory display data.
 *
 * @param min the lowest recommended level (non-negative)
 * @param max the highest recommended level (never below {@code min})
 */
public record LevelRange(int min, int max) {

    /** Canonical constructor validating that both bounds are non-negative and ordered. */
    public LevelRange {
        if (min < 0) {
            throw new IllegalArgumentException("Level range min must not be negative, was " + min);
        }
        if (max < min) {
            throw new IllegalArgumentException(
                "Level range max (" + max + ") must not be below min (" + min + ")");
        }
    }

    /**
     * Renders the band for display, e.g. {@code "levels 20-28"}.
     *
     * @return a human-readable label describing the recommended level band
     */
    public String describe() {
        return "levels " + min + "-" + max;
    }
}
