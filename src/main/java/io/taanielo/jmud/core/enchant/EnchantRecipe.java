package io.taanielo.jmud.core.enchant;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.craft.RecipeMaterial;
import io.taanielo.jmud.core.world.AffixId;

/**
 * An enchanting recipe: the materials and gold a player must supply to an Enchanter to permanently
 * imbue a carried, equippable item with the stat affix identified by {@link #affixId()}.
 *
 * <p>Unlike a crafting {@link io.taanielo.jmud.core.craft.Recipe}, an enchanting recipe has no
 * output item — it names an affix (defined in {@code data/item-affixes.json}) that is appended to
 * the specific item instance being enchanted.
 *
 * @param id              the unique recipe id (e.g. {@code "enchant-of-the-bear"})
 * @param affixId         the id of the affix the recipe attaches; resolved via the affix repository
 * @param goldCost        the gold consumed by the enchantment; never negative
 * @param materials       the non-empty list of material requirements consumed by the enchantment
 * @param minSkill        the minimum enchanting level required to attempt the recipe; {@code 0} means
 *                        no requirement (fully backward compatible with recipes that omit the field)
 * @param proficiencyGain the proficiency points a successful enchant grants in the enchanting
 *                        profession; never negative
 */
public record EnchantRecipe(
    String id,
    AffixId affixId,
    int goldCost,
    List<RecipeMaterial> materials,
    int minSkill,
    int proficiencyGain) {

    /** Default proficiency points granted by a successful enchant when a recipe does not specify one. */
    public static final int DEFAULT_PROFICIENCY_GAIN = 25;

    public EnchantRecipe {
        Objects.requireNonNull(id, "Recipe id is required");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Recipe id must not be blank");
        }
        Objects.requireNonNull(affixId, "Affix id is required");
        if (goldCost < 0) {
            throw new IllegalArgumentException("Gold cost must not be negative");
        }
        materials = List.copyOf(Objects.requireNonNull(materials, "Materials are required"));
        if (materials.isEmpty()) {
            throw new IllegalArgumentException("An enchant recipe must require at least one material");
        }
        if (minSkill < 0) {
            throw new IllegalArgumentException("Minimum skill must not be negative");
        }
        if (proficiencyGain < 0) {
            throw new IllegalArgumentException("Proficiency gain must not be negative");
        }
    }

    /**
     * Creates an enchant recipe with no proficiency requirement and the default proficiency gain,
     * preserving the historical four-argument shape used by older tests and callers.
     *
     * @param id        the unique recipe id
     * @param affixId   the id of the affix the recipe attaches
     * @param goldCost  the gold consumed by the enchantment
     * @param materials the non-empty list of material requirements
     */
    public EnchantRecipe(String id, AffixId affixId, int goldCost, List<RecipeMaterial> materials) {
        this(id, affixId, goldCost, materials, 0, DEFAULT_PROFICIENCY_GAIN);
    }
}
