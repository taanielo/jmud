package io.taanielo.jmud.core.effects.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectMessages;
import io.taanielo.jmud.core.effects.EffectModifier;
import io.taanielo.jmud.core.effects.EffectStacking;
import io.taanielo.jmud.core.effects.ModifierOperation;

public class EffectDefinitionMapper {
    public EffectDefinition toDomain(EffectDefinitionDto dto) {
        Objects.requireNonNull(dto, "Effect definition DTO is required");
        if (dto.schemaVersion() != EffectSchemaVersions.V1) {
            throw new IllegalArgumentException("Unsupported effect schema version " + dto.schemaVersion());
        }
        EffectId id = EffectId.of(dto.id());
        String name = dto.name();
        int durationTicks = dto.durationTicks() == null ? 0 : dto.durationTicks();
        int tickInterval = dto.tickInterval() == null ? 1 : dto.tickInterval();
        EffectStacking stacking = parseStacking(dto.stacking());
        List<EffectModifier> modifiers = mapModifiers(dto.modifiers());
        EffectMessages messages = mapMessages(dto.messages());
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

    private EffectMessages mapMessages(EffectMessageDto dto) {
        if (dto == null) {
            return null;
        }
        return new EffectMessages(
            dto.applySelf(),
            dto.applyRoom(),
            dto.expireSelf(),
            dto.expireRoom(),
            dto.examine()
        );
    }
}
