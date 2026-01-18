package io.taanielo.jmud.core.effects.dto;

import java.util.List;

public record EffectDefinitionDto(
    int schemaVersion,
    String id,
    String name,
    Integer durationTicks,
    Integer tickInterval,
    String stacking,
    List<EffectModifierDto> modifiers,
    EffectMessageDto messages
) {
}
