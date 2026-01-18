package io.taanielo.jmud.core.world.dto;

import java.util.List;

public record ItemDto(
    int schemaVersion,
    String id,
    String name,
    String description,
    ItemAttributesDto attributes,
    List<ItemEffectDto> effects,
    int value
) {
}
