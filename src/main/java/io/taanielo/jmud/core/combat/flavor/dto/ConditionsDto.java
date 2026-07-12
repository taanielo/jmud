package io.taanielo.jmud.core.combat.flavor.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Data-transfer object for {@code data/combat/conditions.json}.
 *
 * @param schemaVersion the file's schema version
 * @param tiers         the ordered percentage bands
 */
public record ConditionsDto(int schemaVersion, @Nullable List<TierDto> tiers) {

    /**
     * A single condition tier.
     *
     * @param minPercent  inclusive lower bound (percent of current/max HP)
     * @param maxPercent  inclusive upper bound
     * @param description the condition phrase
     */
    public record TierDto(int minPercent, int maxPercent, @Nullable String description) {
    }
}
