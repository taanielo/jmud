package io.taanielo.jmud.core.world.dto;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;

public class RoomMapper {

    public RoomDto toDto(Room room) {
        Objects.requireNonNull(room, "Room is required");
        List<String> itemIds = room.getItems().stream()
            .map(item -> item.getId().getValue())
            .toList();
        return new RoomDto(SchemaVersions.V1, room.getId().getValue(), room.getName(), room.getDescription(), itemIds);
    }

    public Room toDomain(RoomDto dto, List<Item> items) {
        Objects.requireNonNull(dto, "Room DTO is required");
        Objects.requireNonNull(items, "Room items are required");
        return new Room(RoomId.of(dto.id()), dto.name(), dto.description(), items);
    }
}
