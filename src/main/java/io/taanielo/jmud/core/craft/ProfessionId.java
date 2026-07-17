package io.taanielo.jmud.core.craft;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Identifier of a crafting profession whose proficiency a player can grow, e.g. {@code blacksmithing}
 * (CRAFT), {@code alchemy} (BREW) or {@code cooking} (COOK).
 *
 * <p>The {@link #value()} doubles as the human-readable profession noun shown to players (e.g. in
 * {@code [requires blacksmithing 3]}) and as the persisted map key on the player record, so it is
 * kept lower-case and non-blank.
 *
 * @param value the lower-case profession id
 */
public record ProfessionId(String value) {

    /** The blacksmithing profession backing the {@code CRAFT} command. */
    public static final ProfessionId BLACKSMITHING = new ProfessionId("blacksmithing");
    /** The alchemy profession backing the {@code BREW} command. */
    public static final ProfessionId ALCHEMY = new ProfessionId("alchemy");
    /** The cooking profession backing the {@code COOK} command. */
    public static final ProfessionId COOKING = new ProfessionId("cooking");
    /** The enchanting profession backing the {@code ENCHANT} command. */
    public static final ProfessionId ENCHANTING = new ProfessionId("enchanting");
    /** The leatherworking profession backing the {@code TAN} command. */
    public static final ProfessionId LEATHERWORKING = new ProfessionId("leatherworking");

    /**
     * All professions known to the game, in a stable display order. New professions must be appended
     * here so read-only surfaces (e.g. {@code SCORE}) pick them up without further edits.
     */
    private static final List<ProfessionId> KNOWN =
        List.of(BLACKSMITHING, ALCHEMY, COOKING, ENCHANTING, LEATHERWORKING);

    public ProfessionId {
        Objects.requireNonNull(value, "Profession id is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Profession id must not be blank");
        }
    }

    /**
     * Returns the profession id for the given raw value, normalised to lower case.
     *
     * @param value the raw profession id; must not be null or blank
     * @return the profession id
     */
    public static ProfessionId of(String value) {
        return new ProfessionId(Objects.requireNonNull(value, "Profession id is required")
            .trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Returns all professions known to the game, in a stable display order.
     *
     * @return an immutable list of every known profession id
     */
    public static List<ProfessionId> known() {
        return KNOWN;
    }
}
