package io.taanielo.jmud.core.enchant.repository.json;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.craft.dto.RecipeMaterialDto;

/**
 * JSON transfer object for an enchanting recipe definition file
 * ({@code data/recipes/enchanting/*.json}).
 *
 * @param schemaVersion the recipe schema version
 * @param id            the unique recipe id
 * @param affix         the id of the affix this enchantment attaches
 * @param goldCost      the gold consumed by the enchantment
 * @param materials     the material requirements consumed by the enchantment
 */
public record EnchantRecipeDto(
    int schemaVersion,
    @Nullable String id,
    @Nullable String affix,
    @Nullable Integer goldCost,
    @Nullable List<RecipeMaterialDto> materials
) {
}
