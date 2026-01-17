package io.taanielo.jmud.core.world.repository;

import java.util.Optional;

import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;

public interface RoomRepository {
    Optional<Room> findById(RoomId id);
}
