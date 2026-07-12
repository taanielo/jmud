package io.taanielo.jmud.core.combat.flavor.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.combat.flavor.DamageVerb;
import io.taanielo.jmud.core.combat.flavor.DamageVerbTable;
import io.taanielo.jmud.core.combat.flavor.DamageVerbTier;
import io.taanielo.jmud.core.combat.flavor.TargetConditionTable;
import io.taanielo.jmud.core.combat.flavor.TargetConditionTier;

/**
 * Maps combat-flavor DTOs loaded from JSON to their domain tables, validating schema versions.
 */
public class CombatFlavorMapper {

    /**
     * Converts a {@link DamageVerbsDto} to a {@link DamageVerbTable}.
     *
     * @param dto the DTO loaded from {@code data/combat/damage-verbs.json}
     * @return the domain damage-verb table
     * @throws IllegalArgumentException if the schema version is unsupported or a tier is malformed
     */
    public DamageVerbTable toDamageVerbTable(DamageVerbsDto dto) {
        Objects.requireNonNull(dto, "Damage verbs DTO is required");
        if (dto.schemaVersion() != CombatFlavorSchemaVersions.V1) {
            throw new IllegalArgumentException("Unsupported damage verbs schema version " + dto.schemaVersion());
        }
        if (dto.tiers() == null || dto.tiers().isEmpty()) {
            throw new IllegalArgumentException("Damage verbs file must define at least one tier");
        }
        List<DamageVerbTier> tiers = new ArrayList<>();
        for (DamageVerbsDto.TierDto tier : dto.tiers()) {
            Objects.requireNonNull(tier, "Damage verb tier is required");
            tiers.add(new DamageVerbTier(
                tier.minPercent(),
                tier.maxPercent(),
                new DamageVerb(requireText(tier.thirdPerson(), "third_person"),
                    requireText(tier.secondPerson(), "second_person"))));
        }
        return new DamageVerbTable(tiers);
    }

    /**
     * Converts a {@link ConditionsDto} to a {@link TargetConditionTable}.
     *
     * @param dto the DTO loaded from {@code data/combat/conditions.json}
     * @return the domain target-condition table
     * @throws IllegalArgumentException if the schema version is unsupported or a tier is malformed
     */
    public TargetConditionTable toConditionTable(ConditionsDto dto) {
        Objects.requireNonNull(dto, "Conditions DTO is required");
        if (dto.schemaVersion() != CombatFlavorSchemaVersions.V1) {
            throw new IllegalArgumentException("Unsupported conditions schema version " + dto.schemaVersion());
        }
        if (dto.tiers() == null || dto.tiers().isEmpty()) {
            throw new IllegalArgumentException("Conditions file must define at least one tier");
        }
        List<TargetConditionTier> tiers = new ArrayList<>();
        for (ConditionsDto.TierDto tier : dto.tiers()) {
            Objects.requireNonNull(tier, "Condition tier is required");
            tiers.add(new TargetConditionTier(
                tier.minPercent(),
                tier.maxPercent(),
                requireText(tier.description(), "description")));
        }
        return new TargetConditionTable(tiers);
    }

    private String requireText(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Combat flavor field '" + field + "' is required");
        }
        return value;
    }
}
