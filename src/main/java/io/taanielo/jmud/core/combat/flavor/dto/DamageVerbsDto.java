package io.taanielo.jmud.core.combat.flavor.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Data-transfer object for {@code data/combat/damage-verbs.json}.
 *
 * @param schemaVersion the file's schema version
 * @param tiers         the ordered percentage bands
 */
public record DamageVerbsDto(int schemaVersion, @Nullable List<TierDto> tiers) {

    /**
     * A single damage-verb tier.
     *
     * @param minPercent   inclusive lower bound (percent of target max HP)
     * @param maxPercent   inclusive upper bound, or {@code null} for the open-ended top tier
     * @param thirdPerson  the third-person-singular verb conjugation
     * @param secondPerson the second-person / base verb conjugation
     */
    public record TierDto(
        int minPercent,
        @Nullable Integer maxPercent,
        @Nullable String thirdPerson,
        @Nullable String secondPerson
    ) {
    }
}
