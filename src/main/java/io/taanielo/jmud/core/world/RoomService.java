package io.taanielo.jmud.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

public class RoomService {

    public record LookResult(List<String> lines, Room room) {
    }

    public record MoveResult(boolean moved, List<String> lines, Room room) {
    }

    private final RoomRepository roomRepository;
    private final RoomId startingRoomId;
    private final ConcurrentHashMap<Username, RoomId> playerLocations = new ConcurrentHashMap<>();

    public RoomService(RoomRepository roomRepository, RoomId startingRoomId) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "Room repository is required");
        this.startingRoomId = Objects.requireNonNull(startingRoomId, "Starting room id is required");
    }

    public RoomId ensurePlayerLocation(Username username) {
        Objects.requireNonNull(username, "Username is required");
        return playerLocations.computeIfAbsent(username, ignored -> startingRoomId);
    }

    public LookResult look(Username username) {
        Objects.requireNonNull(username, "Username is required");
        Room room = loadRoomForPlayer(username);
        if (room == null) {
            return new LookResult(List.of("You are nowhere. The world feels unfinished."), null);
        }
        Room roomWithOccupants = withOccupants(room);
        return new LookResult(describeRoom(roomWithOccupants, username), roomWithOccupants);
    }

    public MoveResult move(Username username, Direction direction) {
        Objects.requireNonNull(username, "Username is required");
        Objects.requireNonNull(direction, "Direction is required");
        Room room = loadRoomForPlayer(username);
        if (room == null) {
            return new MoveResult(false, List.of("You cannot move yet. The world has no rooms."), null);
        }
        RoomId destinationId = room.getExits().get(direction);
        if (destinationId == null) {
            return new MoveResult(false, List.of("You cannot go " + direction.label() + "."), room);
        }
        Room destination = findRoom(destinationId).orElse(null);
        if (destination == null) {
            return new MoveResult(false, List.of("The way " + direction.label() + " is blocked."), room);
        }
        playerLocations.put(username, destinationId);
        Room destinationWithOccupants = withOccupants(destination);
        List<String> lines = new ArrayList<>();
        lines.add("You move " + direction.label() + ".");
        lines.addAll(describeRoom(destinationWithOccupants, username));
        return new MoveResult(true, lines, destinationWithOccupants);
    }

    private Room loadRoomForPlayer(Username username) {
        RoomId roomId = ensurePlayerLocation(username);
        return findRoom(roomId).orElse(null);
    }

    private Optional<Room> findRoom(RoomId roomId) {
        try {
            return roomRepository.findById(roomId);
        } catch (RepositoryException e) {
            return Optional.empty();
        }
    }

    private Room withOccupants(Room room) {
        List<Username> occupants = playerLocations.entrySet().stream()
            .filter(entry -> entry.getValue().equals(room.getId()))
            .map(Entry::getKey)
            .toList();
        return new Room(
            room.getId(),
            room.getName(),
            room.getDescription(),
            room.getExits(),
            room.getItems(),
            occupants
        );
    }

    private List<String> describeRoom(Room room, Username viewer) {
        List<String> lines = new ArrayList<>();
        lines.add(room.getName());
        lines.add(room.getDescription());
        lines.add("Exits: " + formatExits(room.getExits()));
        lines.add("Items: " + formatItems(room.getItems()));
        lines.add("Occupants: " + formatOccupants(room.getOccupants(), viewer));
        return lines;
    }

    private String formatExits(java.util.Map<Direction, RoomId> exits) {
        if (exits.isEmpty()) {
            return "none";
        }
        return exits.keySet().stream()
            .map(Direction::label)
            .sorted()
            .collect(Collectors.joining(", "));
    }

    private String formatItems(List<Item> items) {
        if (items.isEmpty()) {
            return "none";
        }
        return items.stream()
            .map(Item::getName)
            .collect(Collectors.joining(", "));
    }

    private String formatOccupants(List<Username> occupants, Username viewer) {
        List<String> names = occupants.stream()
            .filter(username -> !username.equals(viewer))
            .map(Username::getValue)
            .toList();
        if (names.isEmpty()) {
            return "none";
        }
        return String.join(", ", names);
    }
}
