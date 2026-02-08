package io.taanielo.jmud.core.combat.dto;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.messaging.MessageSpec;
import io.taanielo.jmud.core.messaging.MessageSpecMapper;

public class AttackMapper {
    public AttackDefinition toDomain(AttackDto dto) {
        Objects.requireNonNull(dto, "Attack DTO is required");
        if (dto.schemaVersion() != AttackSchemaVersions.V2) {
            throw new IllegalArgumentException("Unsupported attack schema version " + dto.schemaVersion());
        }
        List<MessageSpec> messages = MessageSpecMapper.fromDtos(dto.messages());
        return new AttackDefinition(
            AttackId.of(dto.id()),
            dto.name(),
            dto.minDamage(),
            dto.maxDamage(),
            dto.hitBonus(),
            dto.critBonus(),
            dto.damageBonus(),
            messages
        );
    }
}
