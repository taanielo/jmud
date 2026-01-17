package io.taanielo.jmud.core.world;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.Value;

import io.taanielo.jmud.core.authentication.Username;

@Value
public class Room {
    RoomId id;
    String name;
    String description;
    Map<Direction, RoomId> exits;
    List<Item> items;
    List<Username> occupants;

    public Room(
        RoomId id,
        String name,
        String description,
        Map<Direction, RoomId> exits,
        List<Item> items,
        List<Username> occupants
    ) {
        this.id = Objects.requireNonNull(id, "Room id is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Room name must not be blank");
        }
        this.name = name;
        this.description = Objects.requireNonNull(description, "Room description is required");
        this.exits = Map.copyOf(Objects.requireNonNull(exits, "Room exits are required"));
        this.items = List.copyOf(Objects.requireNonNull(items, "Room items are required"));
        this.occupants = List.copyOf(Objects.requireNonNull(occupants, "Room occupants are required"));
    }
}
