package io.taanielo.jmud.core.world.dto;

import java.util.Objects;

import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;

public class ItemMapper {

    public ItemDto toDto(Item item) {
        Objects.requireNonNull(item, "Item is required");
        return new ItemDto(SchemaVersions.V1, item.getId().getValue(), item.getName(), item.getDescription());
    }

    public Item toDomain(ItemDto dto) {
        Objects.requireNonNull(dto, "Item DTO is required");
        return new Item(ItemId.of(dto.id()), dto.name(), dto.description());
    }
}
