package io.taanielo.jmud.core.salvage.dto;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.salvage.SalvageMaterial;
import io.taanielo.jmud.core.salvage.SalvageTier;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Rarity;

/**
 * Maps {@link SalvageTierDto} JSON transfer objects to {@link SalvageTier} domain value objects.
 */
public class SalvageTierMapper {

    /**
     * Converts a salvage tier DTO into its domain form, validating required fields.
     *
     * @param dto the salvage tier DTO read from JSON
     * @return the domain salvage tier
     * @throws IllegalArgumentException when a required field is missing or invalid
     */
    public SalvageTier toDomain(SalvageTierDto dto) {
        Objects.requireNonNull(dto, "Salvage tier DTO is required");
        String rarityId = requireText(dto.rarity(), "rarity");
        Rarity rarity = Rarity.fromId(rarityId);
        List<SalvageMaterialDto> materialDtos =
            Objects.requireNonNull(dto.materials(), "Salvage tier '" + rarityId + "' requires materials");
        List<SalvageMaterial> materials = materialDtos.stream()
            .map(m -> toMaterial(rarityId, m))
            .toList();
        return new SalvageTier(rarity, materials);
    }

    private SalvageMaterial toMaterial(String rarityId, SalvageMaterialDto dto) {
        String item = requireText(dto.item(), "material item in salvage tier '" + rarityId + "'");
        int quantity = dto.quantity() == null ? 1 : dto.quantity();
        return new SalvageMaterial(ItemId.of(item), quantity);
    }

    private String requireText(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Salvage tier field '" + field + "' is required");
        }
        return value;
    }
}
