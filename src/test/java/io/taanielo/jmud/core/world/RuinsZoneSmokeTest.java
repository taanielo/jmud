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
 * Integration smoke-test confirming that the Ruins zone rooms are present,
 * connected to each other, and reachable from the existing overworld when
 * loaded from the production data directory.
 */
class RuinsZoneSmokeTest {

    @Test
    void ruins_zoneIsReachableFromExistingWorld() throws RepositoryException {
        JsonItemRepository itemRepository = new JsonItemRepository(Path.of("data"));
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, Path.of("data"));

        Room huntersClearing = roomRepository.findById(RoomId.of("hunters-clearing"))
            .orElseThrow(() -> new AssertionError("Hunter's Clearing room must be present"));
        assertEquals(RoomId.of("ruins-gate"), huntersClearing.getExits().get(Direction.WEST),
            "Hunter's Clearing should have a 'west' exit into the Ruins");

        Room gate = roomRepository.findById(RoomId.of("ruins-gate"))
            .orElseThrow(() -> new AssertionError("Ruins Gate room must be present"));
        assertEquals(RoomId.of("hunters-clearing"), gate.getExits().get(Direction.EAST),
            "Ruins Gate should lead back east to Hunter's Clearing");
        assertEquals(RoomId.of("crumbling-courtyard"), gate.getExits().get(Direction.WEST));

        Room courtyard = roomRepository.findById(RoomId.of("crumbling-courtyard"))
            .orElseThrow(() -> new AssertionError("Crumbling Courtyard room must be present"));
        assertEquals(RoomId.of("ruins-gate"), courtyard.getExits().get(Direction.EAST));
        assertEquals(RoomId.of("collapsed-tower"), courtyard.getExits().get(Direction.NORTH));
        assertEquals(RoomId.of("bandit-captains-den"), courtyard.getExits().get(Direction.WEST));

        Room tower = roomRepository.findById(RoomId.of("collapsed-tower"))
            .orElseThrow(() -> new AssertionError("Collapsed Tower room must be present"));
        assertEquals(RoomId.of("crumbling-courtyard"), tower.getExits().get(Direction.SOUTH));

        Room den = roomRepository.findById(RoomId.of("bandit-captains-den"))
            .orElseThrow(() -> new AssertionError("Bandit Captain's Den room must be present"));
        assertEquals(RoomId.of("crumbling-courtyard"), den.getExits().get(Direction.EAST));
    }

    @Test
    void ruins_roomsHaveDescriptions() throws RepositoryException {
        JsonItemRepository itemRepository = new JsonItemRepository(Path.of("data"));
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, Path.of("data"));

        for (String roomId : new String[] {
            "ruins-gate", "crumbling-courtyard", "collapsed-tower", "bandit-captains-den"
        }) {
            Optional<Room> room = roomRepository.findById(RoomId.of(roomId));
            assertTrue(room.isPresent(), "Room " + roomId + " must be present");
            assertTrue(room.get().getDescription() != null && !room.get().getDescription().isBlank(),
                "Room " + roomId + " must have a description");
        }
    }
}
