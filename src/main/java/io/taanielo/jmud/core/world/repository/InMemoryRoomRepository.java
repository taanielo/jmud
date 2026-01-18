package io.taanielo.jmud.core.world.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;

public class InMemoryRoomRepository implements RoomRepository {

    private final Map<RoomId, Room> rooms;

    public InMemoryRoomRepository() {
        RoomId trainingYardId = RoomId.of("training-yard");
        RoomId armoryId = RoomId.of("armory");
        RoomId courtyardId = RoomId.of("courtyard");

        Room trainingYard = new Room(
            trainingYardId,
            "Training Yard",
            "A dusty yard with practice dummies and scattered weapons.",
            Map.of(Direction.NORTH, armoryId, Direction.EAST, courtyardId),
            List.of(new Item(ItemId.of("iron-sword"), "Iron Sword", "A plain iron sword with a worn leather grip.")),
            List.of()
        );

        Room armory = new Room(
            armoryId,
            "Armory",
            "A compact room lined with weapon racks and oiled leather armor.",
            Map.of(Direction.SOUTH, trainingYardId),
            List.of(new Item(ItemId.of("training-shield"), "Training Shield", "A wooden shield used for drills.")),
            List.of()
        );

        Room courtyard = new Room(
            courtyardId,
            "Courtyard",
            "A breezy courtyard with a stone fountain in the center.",
            Map.of(Direction.WEST, trainingYardId),
            List.of(),
            List.of()
        );

        this.rooms = new ConcurrentHashMap<>();
        this.rooms.put(trainingYardId, trainingYard);
        this.rooms.put(armoryId, armory);
        this.rooms.put(courtyardId, courtyard);
    }

    @Override
    public void save(Room room) throws RepositoryException {
        if (room == null) {
            throw new RepositoryException("Room is required");
        }
        rooms.put(room.getId(), room);
    }

    @Override
    public Optional<Room> findById(RoomId id) throws RepositoryException {
        if (id == null) {
            throw new RepositoryException("Room id is required");
        }
        return Optional.ofNullable(rooms.get(id));
    }
}
