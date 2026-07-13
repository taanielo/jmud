package io.taanielo.jmud.core.world.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Maps between {@link Room} domain objects and {@link RoomDto} transfer objects.
 */
public class RoomMapper {

    /**
     * Converts a domain room to its DTO representation.
     */
    public RoomDto toDto(Room room) {
        Objects.requireNonNull(room, "Room is required");
        List<String> itemIds = room.getItems().stream()
            .map(item -> item.getId().getValue())
            .toList();
        Map<String, String> exits = new HashMap<>();
        room.getExits().forEach((dir, roomId) -> exits.put(dir.name().toLowerCase(Locale.ROOT), roomId.getValue()));
        Map<String, String> lockedExits = new HashMap<>();
        room.getLockedExits().forEach((dir, keyId) -> lockedExits.put(dir.name().toLowerCase(Locale.ROOT), keyId.getValue()));
        Map<String, String> hiddenExits = new HashMap<>();
        room.getHiddenExits().forEach((dir, roomId) -> hiddenExits.put(dir.name().toLowerCase(Locale.ROOT), roomId.getValue()));
        Integer minLevel = room.getMinLevel();
        String nightDescription = room.getNightDescription();
        Integer lightLevel = room.getLightLevel();
        boolean outdoor = room.isOutdoor();
        List<String> ambientMessages = room.getAmbientMessages();
        int version;
        if (!hiddenExits.isEmpty()) {
            version = SchemaVersions.V9;
        } else if (!ambientMessages.isEmpty()) {
            version = SchemaVersions.V8;
        } else if (outdoor) {
            version = SchemaVersions.V7;
        } else if (lightLevel != null) {
            version = SchemaVersions.V6;
        } else if (nightDescription != null) {
            version = SchemaVersions.V5;
        } else if (minLevel != null) {
            version = SchemaVersions.V4;
        } else {
            version = lockedExits.isEmpty() ? SchemaVersions.V2 : SchemaVersions.V3;
        }
        return new RoomDto(version, room.getId().getValue(), room.getName(), room.getDescription(), itemIds, exits,
            lockedExits.isEmpty() ? null : lockedExits, minLevel, nightDescription, lightLevel,
            outdoor ? Boolean.TRUE : null, ambientMessages.isEmpty() ? null : ambientMessages,
            hiddenExits.isEmpty() ? null : hiddenExits);
    }

    /**
     * Converts a DTO and resolved item list back to a domain room.
     */
    public Room toDomain(RoomDto dto, List<Item> items) {
        Objects.requireNonNull(dto, "Room DTO is required");
        Objects.requireNonNull(items, "Room items are required");
        Map<Direction, RoomId> exits = new HashMap<>();
        if (dto.exits() != null) {
            for (Map.Entry<String, String> entry : dto.exits().entrySet()) {
                Direction.fromInput(entry.getKey()).ifPresent(
                    dir -> exits.put(dir, RoomId.of(entry.getValue()))
                );
            }
        }
        Map<Direction, ItemId> lockedExits = new HashMap<>();
        if (dto.lockedExits() != null) {
            for (Map.Entry<String, String> entry : dto.lockedExits().entrySet()) {
                Direction.fromInput(entry.getKey()).ifPresent(
                    dir -> lockedExits.put(dir, ItemId.of(entry.getValue()))
                );
            }
        }
        Map<Direction, RoomId> hiddenExits = new HashMap<>();
        if (dto.hiddenExits() != null) {
            for (Map.Entry<String, String> entry : dto.hiddenExits().entrySet()) {
                Direction.fromInput(entry.getKey()).ifPresent(
                    dir -> hiddenExits.put(dir, RoomId.of(entry.getValue()))
                );
            }
        }
        List<String> ambientMessages = dto.ambientMessages() == null ? List.of() : dto.ambientMessages();
        return new Room(
            RoomId.of(dto.id()),
            dto.name(),
            dto.description(),
            exits,
            items,
            List.of(),
            lockedExits,
            dto.minLevel(),
            dto.nightDescription(),
            dto.lightLevel(),
            Boolean.TRUE.equals(dto.isOutdoor()),
            ambientMessages,
            hiddenExits
        );
    }
}
