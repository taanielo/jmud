package io.taanielo.jmud.core.craft;

import java.util.Locale;
import java.util.Objects;

/**
 * Value object describing the NPC profession that backs a {@link CraftingService}, used to
 * parameterize the player-facing wording so a single service class can serve both the blacksmith's
 * {@code CRAFT} and the alchemist's {@code BREW} without duplicating logic.
 *
 * @param crafter     the crafter noun used in messages (e.g. {@code "blacksmith"}, {@code "alchemist"})
 * @param command     the command verb shown to the player in upper case (e.g. {@code "CRAFT"}, {@code "BREW"})
 * @param verb        the lower-case action verb (e.g. {@code "craft"}, {@code "brew"})
 * @param craftAction the success phrase describing what the crafter does with the materials
 *                    (e.g. {@code "works your materials into"}, {@code "brews your herbs into"})
 * @param profession  the profession whose proficiency this crafter trains (e.g. {@code blacksmithing})
 */
public record CrafterProfile(
    String crafter, String command, String verb, String craftAction, ProfessionId profession) {

    public CrafterProfile {
        Objects.requireNonNull(crafter, "Crafter is required");
        Objects.requireNonNull(command, "Command is required");
        Objects.requireNonNull(verb, "Verb is required");
        Objects.requireNonNull(craftAction, "Craft action is required");
        Objects.requireNonNull(profession, "Profession is required");
    }

    /** Returns the default blacksmith profile used by the {@code CRAFT} command. */
    public static CrafterProfile blacksmith() {
        return new CrafterProfile("blacksmith", "CRAFT", "craft", "works your materials into",
            ProfessionId.BLACKSMITHING);
    }

    /** Returns the alchemist profile used by the {@code BREW} command. */
    public static CrafterProfile alchemist() {
        return new CrafterProfile("alchemist", "BREW", "brew", "brews your herbs into",
            ProfessionId.ALCHEMY);
    }

    /** Returns the cook profile used by the {@code COOK} command. */
    public static CrafterProfile cook() {
        return new CrafterProfile("cook", "COOK", "cook", "cooks your ingredients into",
            ProfessionId.COOKING);
    }

    /** Returns the leatherworker profile used by the {@code TAN} command. */
    public static CrafterProfile leatherworker() {
        return new CrafterProfile("leatherworker", "TAN", "tan", "tans your hides into",
            ProfessionId.LEATHERWORKING);
    }

    /** Returns the action verb with its first letter capitalized (e.g. {@code "Craft"}, {@code "Brew"}). */
    public String capitalizedVerb() {
        if (verb.isEmpty()) {
            return verb;
        }
        return verb.substring(0, 1).toUpperCase(Locale.ROOT) + verb.substring(1);
    }
}
