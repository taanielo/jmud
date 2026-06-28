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
    /** Exits that start locked and the key item id required to unlock each one. */
    Map<Direction, ItemId> lockedExits;

    /**
     * Constructs a room with no locked exits. Existing callsites use this overload.
     */
    public Room(
        RoomId id,
        String name,
        String description,
        Map<Direction, RoomId> exits,
        List<Item> items,
        List<Username> occupants
    ) {
        this(id, name, description, exits, items, occupants, Map.of());
    }

    /**
     * Constructs a room with the specified locked exits configuration.
     *
     * @param lockedExits map of direction to required key item id for exits that start locked
     */
    public Room(
        RoomId id,
        String name,
        String description,
        Map<Direction, RoomId> exits,
        List<Item> items,
        List<Username> occupants,
        Map<Direction, ItemId> lockedExits
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
        this.lockedExits = Map.copyOf(Objects.requireNonNull(lockedExits, "Locked exits map is required"));
    }
}
