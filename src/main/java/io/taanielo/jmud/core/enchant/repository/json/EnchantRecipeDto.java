package io.taanielo.jmud.core.enchant.repository.json;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.craft.dto.RecipeMaterialDto;

/**
 * JSON transfer object for an enchanting recipe definition file
 * ({@code data/recipes/enchanting/*.json}).
 *
 * @param schemaVersion   the recipe schema version
 * @param id              the unique recipe id
 * @param affix           the id of the affix this enchantment attaches
 * @param goldCost        the gold consumed by the enchantment
 * @param materials       the material requirements consumed by the enchantment
 * @param minSkill        the optional minimum enchanting level required to attempt the recipe
 *                        (defaults to {@code 0}, i.e. no requirement, when omitted)
 * @param proficiencyGain the optional proficiency points a successful enchant grants (defaults to the
 *                        engine default when omitted)
 */
public record EnchantRecipeDto(
    int schemaVersion,
    @Nullable String id,
    @Nullable String affix,
    @Nullable Integer goldCost,
    @Nullable List<RecipeMaterialDto> materials,
    @Nullable Integer minSkill,
    @Nullable Integer proficiencyGain
) {
}
