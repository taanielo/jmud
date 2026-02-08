package io.taanielo.jmud.core.character.dto;

import java.util.Objects;

import io.taanielo.jmud.core.character.Race;
import io.taanielo.jmud.core.character.RaceId;

public class RaceMapper {
    public Race toDomain(RaceDto dto) {
        Objects.requireNonNull(dto, "Race DTO is required");
        if (dto.schemaVersion() != RaceSchemaVersions.V2) {
            throw new IllegalArgumentException("Unsupported race schema version " + dto.schemaVersion());
        }
        RaceHealingDto healingDto = Objects.requireNonNull(dto.healing(), "Race healing is required");
        return new Race(
            RaceId.of(dto.id()),
            dto.name(),
            healingDto.baseModifier(),
            dto.carryBase()
        );
    }
}
