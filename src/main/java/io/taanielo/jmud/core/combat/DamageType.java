package io.taanielo.jmud.core.combat;

import java.util.Locale;

/**
 * Classifies the elemental nature of the damage an {@link AttackDefinition} deals.
 *
 * <p>{@link #PHYSICAL} is the default for every weapon and mob attack that does not declare a
 * {@code damage_type}; it is never mitigated by elemental resistance. Every other type is an
 * element a defender can resist through equipped armour that carries the matching
 * {@code *_resist} stat (see {@link #resistStatKey()}), for example {@code "fire_resist"} on a
 * cloak reducing incoming {@link #FIRE} damage.
 *
 * <p>The stat convention deliberately reuses the item's free-form {@code attributes.stats} map —
 * the same mechanism that already carries {@code "ac"} — so no item schema change is required to
 * itemize resistance.
 */
public enum DamageType {

    /** Ordinary weapon damage; never reduced by elemental resistance. */
    PHYSICAL(null),

    /** Fire/heat damage; mitigated by the {@code "fire_resist"} armour stat. */
    FIRE("fire_resist"),

    /** Cold/frost damage; mitigated by the {@code "cold_resist"} armour stat. */
    COLD("cold_resist"),

    /** Poison/venom damage; mitigated by the {@code "poison_resist"} armour stat. */
    POISON("poison_resist");

    private final String resistStatKey;

    DamageType(String resistStatKey) {
        this.resistStatKey = resistStatKey;
    }

    /**
     * Returns the item stat key that resists this damage type, or {@code null} for
     * {@link #PHYSICAL} which cannot be resisted.
     *
     * @return the {@code attributes.stats} key that mitigates this type, or {@code null}
     */
    public String resistStatKey() {
        return resistStatKey;
    }

    /**
     * Returns whether this type is subject to elemental resistance mitigation.
     *
     * @return {@code true} for every type except {@link #PHYSICAL}
     */
    public boolean isResistible() {
        return resistStatKey != null;
    }

    /**
     * Returns the plain, lower-case player-facing word for this damage type (e.g. {@code "fire"},
     * {@code "cold"}, {@code "poison"}), rather than the upper-case Java enum constant. This is the
     * canonical wording surfaced to players wherever an element is named outside of a full combat
     * strike sentence — for example the {@code CONSIDER} elemental-affinity line — so the terminology
     * never diverges between screens.
     *
     * @return the lower-case player-facing name of this damage type
     */
    public String displayName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a case-insensitive damage-type name, defaulting to {@link #PHYSICAL} when the value
     * is {@code null}, blank, or unrecognised. Used by the JSON attack mapper so that existing
     * attack files (which omit {@code damage_type}) load as {@link #PHYSICAL} unchanged.
     *
     * @param raw the raw value from data; may be {@code null}
     * @return the matching {@link DamageType}, or {@link #PHYSICAL} when absent/unknown
     */
    public static DamageType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return PHYSICAL;
        }
        try {
            return valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PHYSICAL;
        }
    }
}
