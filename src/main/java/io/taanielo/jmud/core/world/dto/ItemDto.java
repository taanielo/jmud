package io.taanielo.jmud.core.world.dto;

import java.util.List;

import io.taanielo.jmud.core.messaging.dto.MessageSpecDto;

public record ItemDto(
    int schemaVersion,
    String id,
    String name,
    String description,
    ItemAttributesDto attributes,
    List<ItemEffectDto> effects,
    List<MessageSpecDto> messages,
    String equipSlot,
    int weight,
    int value
) {
}
