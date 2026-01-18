package io.taanielo.jmud.core.character.dto;

import java.util.Objects;

import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;

public class ClassMapper {
    public ClassDefinition toDomain(ClassDto dto) {
        Objects.requireNonNull(dto, "Class DTO is required");
        if (dto.schemaVersion() != ClassSchemaVersions.V1) {
            throw new IllegalArgumentException("Unsupported class schema version " + dto.schemaVersion());
        }
        ClassHealingDto healingDto = Objects.requireNonNull(dto.healing(), "Class healing is required");
        return new ClassDefinition(
            ClassId.of(dto.id()),
            dto.name(),
            healingDto.baseModifier()
        );
    }
}
