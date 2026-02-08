package io.taanielo.jmud.core.effects.dto;

import java.util.List;

import io.taanielo.jmud.core.messaging.dto.MessageSpecDto;

public record EffectDefinitionDto(
    int schemaVersion,
    String id,
    String name,
    Integer durationTicks,
    Integer tickInterval,
    String stacking,
    List<EffectModifierDto> modifiers,
    List<MessageSpecDto> messages
) {
}
