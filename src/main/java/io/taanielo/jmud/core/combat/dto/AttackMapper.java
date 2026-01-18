package io.taanielo.jmud.core.combat.dto;

import java.util.Objects;

import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;

public class AttackMapper {
    public AttackDefinition toDomain(AttackDto dto) {
        Objects.requireNonNull(dto, "Attack DTO is required");
        if (dto.schemaVersion() != AttackSchemaVersions.V1) {
            throw new IllegalArgumentException("Unsupported attack schema version " + dto.schemaVersion());
        }
        return new AttackDefinition(
            AttackId.of(dto.id()),
            dto.name(),
            dto.minDamage(),
            dto.maxDamage(),
            dto.hitBonus(),
            dto.critBonus(),
            dto.damageBonus()
        );
    }
}
