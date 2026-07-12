package io.taanielo.jmud.core.character.dto;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.character.AttributeBonus;
import io.taanielo.jmud.core.character.AttributeGainSchedule;
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.LevelGains;

public class ClassMapper {
    public ClassDefinition toDomain(ClassDto dto) {
        Objects.requireNonNull(dto, "Class DTO is required");
        if (dto.schemaVersion() != ClassSchemaVersions.V2
            && dto.schemaVersion() != ClassSchemaVersions.V3
            && dto.schemaVersion() != ClassSchemaVersions.V4
            && dto.schemaVersion() != ClassSchemaVersions.V5
            && dto.schemaVersion() != ClassSchemaVersions.V6
            && dto.schemaVersion() != ClassSchemaVersions.V7) {
            throw new IllegalArgumentException("Unsupported class schema version " + dto.schemaVersion());
        }
        ClassHealingDto healingDto = Objects.requireNonNull(dto.healing(), "Class healing is required");
        List<AbilityId> startingAbilityIds = dto.abilityIds() == null
            ? List.of()
            : dto.abilityIds().stream().map(AbilityId::of).toList();
        List<AbilityId> trainableAbilityIds = dto.trainableAbilityIds() == null
            ? List.of()
            : dto.trainableAbilityIds().stream().map(AbilityId::of).toList();
        String description = dto.description() == null ? "" : dto.description();
        LevelGains levelGains = dto.levelGains() == null
            ? LevelGains.DEFAULT
            : new LevelGains(dto.levelGains().hp(), dto.levelGains().mana(), dto.levelGains().move());
        AttributeBonus attributeBonus = AttributeBonusMapper.toDomain(dto.attributeBonuses());
        AttributeGainSchedule attributeGains = AttributeBonusMapper.toDomain(dto.attributeGains());
        return new ClassDefinition(
            ClassId.of(dto.id()),
            dto.name(),
            healingDto.baseModifier(),
            dto.carryBonus(),
            dto.armorBonus(),
            startingAbilityIds,
            trainableAbilityIds,
            description,
            levelGains,
            attributeBonus,
            attributeGains
        );
    }
}
