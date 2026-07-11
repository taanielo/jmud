package io.taanielo.jmud.core.craft;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.world.ItemId;

/**
 * A crafting recipe: the materials and gold a player must supply to a blacksmith to receive a single
 * output item.
 *
 * @param id              the unique recipe id
 * @param name            the human-readable recipe name (typically the output item's name)
 * @param outputItemId    the id of the item produced on a successful craft
 * @param goldCost        the gold consumed by the craft; never negative
 * @param materials       the non-empty list of material requirements consumed by the craft
 * @param minSkill        the minimum profession level required to attempt the recipe; {@code 0} means
 *                        no requirement (fully backward compatible with recipes that omit the field)
 * @param proficiencyGain the proficiency points a successful craft grants in the profession; never
 *                        negative
 */
public record Recipe(
    RecipeId id,
    String name,
    ItemId outputItemId,
    int goldCost,
    List<RecipeMaterial> materials,
    int minSkill,
    int proficiencyGain) {

    /** Default proficiency points granted by a successful craft when a recipe does not specify one. */
    public static final int DEFAULT_PROFICIENCY_GAIN = 25;

    public Recipe {
        Objects.requireNonNull(id, "Recipe id is required");
        Objects.requireNonNull(name, "Recipe name is required");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Recipe name must not be blank");
        }
        Objects.requireNonNull(outputItemId, "Output item id is required");
        if (goldCost < 0) {
            throw new IllegalArgumentException("Gold cost must not be negative");
        }
        materials = List.copyOf(Objects.requireNonNull(materials, "Materials are required"));
        if (materials.isEmpty()) {
            throw new IllegalArgumentException("A recipe must require at least one material");
        }
        if (minSkill < 0) {
            throw new IllegalArgumentException("Minimum skill must not be negative");
        }
        if (proficiencyGain < 0) {
            throw new IllegalArgumentException("Proficiency gain must not be negative");
        }
    }

    /**
     * Creates a recipe with no proficiency requirement and the default proficiency gain, preserving
     * the historical five-argument shape used by older tests and callers.
     *
     * @param id           the unique recipe id
     * @param name         the human-readable recipe name
     * @param outputItemId the id of the item produced on a successful craft
     * @param goldCost     the gold consumed by the craft
     * @param materials    the non-empty list of material requirements
     */
    public Recipe(RecipeId id, String name, ItemId outputItemId, int goldCost, List<RecipeMaterial> materials) {
        this(id, name, outputItemId, goldCost, materials, 0, DEFAULT_PROFICIENCY_GAIN);
    }
}
