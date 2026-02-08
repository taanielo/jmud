package io.taanielo.jmud.core.effects.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.messaging.MessageSpec;
import io.taanielo.jmud.core.messaging.MessageSpecMapper;
import io.taanielo.jmud.core.effects.EffectModifier;
import io.taanielo.jmud.core.effects.EffectStacking;
import io.taanielo.jmud.core.effects.ModifierOperation;

public class EffectDefinitionMapper {
    public EffectDefinition toDomain(EffectDefinitionDto dto) {
        Objects.requireNonNull(dto, "Effect definition DTO is required");
        validateSchema(dto);
        EffectId id = EffectId.of(dto.id());
        String name = dto.name();
        int durationTicks = dto.durationTicks() == null ? 0 : dto.durationTicks();
        int tickInterval = dto.tickInterval() == null ? 1 : dto.tickInterval();
        EffectStacking stacking = parseStacking(dto.stacking());
        List<EffectModifier> modifiers = mapModifiers(dto.modifiers());
        List<MessageSpec> messages = MessageSpecMapper.fromDtos(dto.messages());
        return new EffectDefinition(id, name, durationTicks, tickInterval, stacking, modifiers, messages);
    }

    private EffectStacking parseStacking(String value) {
        if (value == null || value.isBlank()) {
            return EffectStacking.REFRESH;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return EffectStacking.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown stacking value " + value);
        }
    }

    private List<EffectModifier> mapModifiers(List<EffectModifierDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        List<EffectModifier> mapped = new ArrayList<>();
        for (EffectModifierDto dto : dtos) {
            if (dto == null) {
                continue;
            }
            ModifierOperation operation = parseOperation(dto.op());
            mapped.add(new EffectModifier(dto.stat(), operation, dto.amount()));
        }
        return mapped;
    }

    private ModifierOperation parseOperation(String op) {
        if (op == null || op.isBlank()) {
            return ModifierOperation.ADD;
        }
        String normalized = op.trim().toUpperCase(Locale.ROOT);
        try {
            return ModifierOperation.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown modifier operation " + op);
        }
    }

    private void validateSchema(EffectDefinitionDto dto) {
        if (dto.schemaVersion() != EffectSchemaVersions.V2) {
            throw new IllegalArgumentException("Unsupported effect schema version " + dto.schemaVersion());
        }
    }
}
