package io.taanielo.jmud.core.world;

/**
 * Identification facet of an {@link Item}: whether its true nature — rarity tier and affixes — is
 * known to its holder. This is a construction-time value object so item features can grow without
 * reshaping {@link Item}'s constructor. Unidentified items display generically and hide their rarity
 * coloring and affix stats until revealed. Defaults to identified so plain items and legacy data
 * (which has no {@code identified} field) are identified out of the box.
 *
 * @param identified whether the item's rarity and affixes are revealed
 */
public record Identification(boolean identified) {

    private static final Identification KNOWN = new Identification(true);
    private static final Identification UNKNOWN = new Identification(false);

    /**
     * Returns the shared identified (fully known) state.
     */
    public static Identification known() {
        return KNOWN;
    }

    /**
     * Returns the shared unidentified state.
     */
    public static Identification unidentified() {
        return UNKNOWN;
    }

    /**
     * Returns the identification state for the given flag.
     *
     * @param identified whether the item's rarity and affixes are revealed
     */
    public static Identification of(boolean identified) {
        return identified ? KNOWN : UNKNOWN;
    }
}
