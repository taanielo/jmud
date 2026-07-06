package io.taanielo.jmud.core.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.json.JsonItemRepository;
import io.taanielo.jmud.core.world.repository.json.JsonRoomRepository;

/**
 * Integration smoke-test confirming that the Sewers zone rooms are present,
 * connected to each other, and reachable from the existing world graph when
 * loaded from the production data directory.
 */
class SewersZoneSmokeTest {

    @Test
    void sewers_zoneIsReachableFromExistingWorld() throws RepositoryException {
        JsonItemRepository itemRepository = new JsonItemRepository(Path.of("data"));
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, Path.of("data"));

        Room wardenChamber = roomRepository.findById(RoomId.of("warden-chamber"))
            .orElseThrow(() -> new AssertionError("Warden Chamber room must be present"));
        assertEquals(RoomId.of("sewers-entrance"), wardenChamber.getExits().get(Direction.DOWN),
            "Warden Chamber should have a 'down' exit into the Sewers");

        Room entrance = roomRepository.findById(RoomId.of("sewers-entrance"))
            .orElseThrow(() -> new AssertionError("Sewers Entrance room must be present"));
        assertEquals(RoomId.of("warden-chamber"), entrance.getExits().get(Direction.UP),
            "Sewers Entrance should lead back up to the Warden Chamber");
        assertEquals(RoomId.of("sewers-tunnel"), entrance.getExits().get(Direction.EAST));

        Room tunnel = roomRepository.findById(RoomId.of("sewers-tunnel"))
            .orElseThrow(() -> new AssertionError("Sewers Tunnel room must be present"));
        assertEquals(RoomId.of("sewers-entrance"), tunnel.getExits().get(Direction.WEST));
        assertEquals(RoomId.of("sewers-cistern"), tunnel.getExits().get(Direction.SOUTH));

        Room cistern = roomRepository.findById(RoomId.of("sewers-cistern"))
            .orElseThrow(() -> new AssertionError("Sewers Cistern room must be present"));
        assertEquals(RoomId.of("sewers-tunnel"), cistern.getExits().get(Direction.NORTH));
    }

    @Test
    void sewers_roomsHaveDescriptions() throws RepositoryException {
        JsonItemRepository itemRepository = new JsonItemRepository(Path.of("data"));
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, Path.of("data"));

        for (String roomId : new String[] { "sewers-entrance", "sewers-tunnel", "sewers-cistern" }) {
            Optional<Room> room = roomRepository.findById(RoomId.of(roomId));
            assertTrue(room.isPresent(), "Room " + roomId + " must be present");
            assertTrue(room.get().getDescription() != null && !room.get().getDescription().isBlank(),
                "Room " + roomId + " must have a description");
        }
    }
}
