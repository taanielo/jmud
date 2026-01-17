package io.taanielo.jmud.core.world;

import java.util.List;
import java.util.Objects;

import lombok.Value;

@Value
public class Room {
    RoomId id;
    String name;
    String description;
    List<Item> items;

    public Room(RoomId id, String name, String description, List<Item> items) {
        this.id = Objects.requireNonNull(id, "Room id is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Room name must not be blank");
        }
        this.name = name;
        this.description = Objects.requireNonNull(description, "Room description is required");
        this.items = List.copyOf(Objects.requireNonNull(items, "Room items are required"));
    }
}
