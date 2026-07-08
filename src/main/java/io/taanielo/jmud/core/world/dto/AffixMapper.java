package io.taanielo.jmud.core.world.dto;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.taanielo.jmud.core.world.AffixId;
import io.taanielo.jmud.core.world.ItemAffix;
import io.taanielo.jmud.core.world.Rarity;

/**
 * Maps {@link AffixDto} wire records to the domain {@link ItemAffix} value type, keeping Jackson
 * concerns out of the domain layer (AGENTS.md §3.2).
 */
public class AffixMapper {

    /**
     * Converts a wire affix definition into its domain form.
     *
     * @param dto the affix DTO read from JSON
     * @return the domain affix
     * @throws IllegalArgumentException if the DTO carries invalid data (blank id/label, unknown
     *                                  rarity tier, or no allowed rarities)
     */
    public ItemAffix toDomain(AffixDto dto) {
        Objects.requireNonNull(dto, "Affix DTO is required");
        Map<String, Integer> stats = dto.stats() == null ? Map.of() : dto.stats();
        Set<Rarity> allowed = EnumSet.noneOf(Rarity.class);
        if (dto.allowedRarities() != null) {
            for (String rarityId : dto.allowedRarities()) {
                allowed.add(Rarity.fromId(rarityId));
            }
        }
        return new ItemAffix(AffixId.of(dto.id()), dto.label(), stats, allowed);
    }
}
