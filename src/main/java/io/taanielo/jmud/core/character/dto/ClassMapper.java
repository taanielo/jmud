package io.taanielo.jmud.core.character.dto;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;

public class ClassMapper {
    public ClassDefinition toDomain(ClassDto dto) {
        Objects.requireNonNull(dto, "Class DTO is required");
        if (dto.schemaVersion() != ClassSchemaVersions.V2) {
            throw new IllegalArgumentException("Unsupported class schema version " + dto.schemaVersion());
        }
        ClassHealingDto healingDto = Objects.requireNonNull(dto.healing(), "Class healing is required");
        List<AbilityId> startingAbilityIds = dto.abilityIds() == null
            ? List.of()
            : dto.abilityIds().stream().map(AbilityId::of).toList();
        return new ClassDefinition(
            ClassId.of(dto.id()),
            dto.name(),
            healingDto.baseModifier(),
            dto.carryBonus(),
            startingAbilityIds
        );
    }
}
