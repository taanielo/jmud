package io.taanielo.jmud.core.craft.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * JSON transfer object for a crafting recipe definition file ({@code data/recipes/*.json}).
 *
 * @param schemaVersion   the recipe schema version
 * @param id              the unique recipe id
 * @param name            the human-readable recipe name
 * @param outputItem      the id of the item produced on a successful craft
 * @param goldCost        the gold consumed by the craft
 * @param materials       the material requirements consumed by the craft
 * @param minSkill        the optional minimum profession level required to attempt the recipe
 *                        (defaults to {@code 0}, i.e. no requirement, when omitted)
 * @param proficiencyGain the optional proficiency points a successful craft grants (defaults to the
 *                        engine default when omitted)
 */
public record RecipeDto(
    int schemaVersion,
    @Nullable String id,
    @Nullable String name,
    @Nullable String outputItem,
    @Nullable Integer goldCost,
    @Nullable List<RecipeMaterialDto> materials,
    @Nullable Integer minSkill,
    @Nullable Integer proficiencyGain
) {
}
