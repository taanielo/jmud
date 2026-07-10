package io.taanielo.jmud.core.craft.dto;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.craft.Recipe;
import io.taanielo.jmud.core.craft.RecipeId;
import io.taanielo.jmud.core.craft.RecipeMaterial;
import io.taanielo.jmud.core.world.ItemId;

/**
 * Maps {@link RecipeDto} JSON transfer objects to {@link Recipe} domain value objects.
 */
public class RecipeMapper {

    /**
     * Converts a recipe DTO into its domain form, validating required fields.
     *
     * @param dto the recipe DTO read from JSON
     * @return the domain recipe
     * @throws IllegalArgumentException when a required field is missing or invalid
     */
    public Recipe toDomain(RecipeDto dto) {
        Objects.requireNonNull(dto, "Recipe DTO is required");
        String id = requireText(dto.id(), "id");
        String name = requireText(dto.name(), "name");
        String outputItem = requireText(dto.outputItem(), "output_item");
        int goldCost = dto.goldCost() == null ? 0 : dto.goldCost();
        List<RecipeMaterialDto> materialDtos =
            Objects.requireNonNull(dto.materials(), "Recipe '" + id + "' requires materials");
        List<RecipeMaterial> materials = materialDtos.stream()
            .map(m -> toMaterial(id, m))
            .toList();
        return new Recipe(RecipeId.of(id), name, ItemId.of(outputItem), goldCost, materials);
    }

    private RecipeMaterial toMaterial(String recipeId, RecipeMaterialDto dto) {
        String item = requireText(dto.item(), "material item in recipe '" + recipeId + "'");
        int quantity = dto.quantity() == null ? 1 : dto.quantity();
        return new RecipeMaterial(ItemId.of(item), quantity);
    }

    private String requireText(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Recipe field '" + field + "' is required");
        }
        return value;
    }
}
