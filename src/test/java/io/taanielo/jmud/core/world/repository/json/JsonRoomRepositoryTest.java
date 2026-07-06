package io.taanielo.jmud.core.world.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.repository.RepositoryException;

class JsonRoomRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsRoomWithItems() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository itemRepository = new JsonItemRepository(dataRoot);
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, dataRoot);

        Item sword = new Item(
            ItemId.of("practice-sword"),
            "Practice Sword",
            "A blunted sword for sparring.",
            ItemAttributes.empty(),
            List.of(),
            List.of(),
            null,
            3,
            12,
            null
        );
        Item potion = new Item(
            ItemId.of("minor-potion"),
            "Minor Potion",
            "A small vial for quick recovery.",
            ItemAttributes.empty(),
            List.of(),
            List.of(),
            null,
            1,
            8,
            null
        );
        itemRepository.save(sword);
        itemRepository.save(potion);

        Room room = new Room(
            RoomId.of("sparring-ring"),
            "Sparring Ring",
            "A chalked ring surrounded by straw dummies.",
            Map.of(),
            List.of(sword, potion),
            List.of()
        );
        roomRepository.save(room);

        Optional<Room> loaded = roomRepository.findById(RoomId.of("sparring-ring"));

        assertTrue(loaded.isPresent());
        assertEquals(room, loaded.get());
    }

    @Test
    void savesAndLoadsRoomWithMinLevel() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository itemRepository = new JsonItemRepository(dataRoot);
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, dataRoot);

        Room room = new Room(
            RoomId.of("catacombs"),
            "Catacombs",
            "A dank, dangerous passage.",
            Map.of(),
            List.of(),
            List.of(),
            Map.of(),
            5
        );
        roomRepository.save(room);

        // Use a fresh repository instance so the read goes through the JSON file, not the cache.
        JsonRoomRepository reloadedRepository = new JsonRoomRepository(itemRepository, dataRoot);
        Optional<Room> loaded = reloadedRepository.findById(RoomId.of("catacombs"));

        assertTrue(loaded.isPresent());
        assertEquals(Integer.valueOf(5), loaded.get().getMinLevel());
    }

    @Test
    void savesAndLoadsRoomWithNightDescription() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository itemRepository = new JsonItemRepository(dataRoot);
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, dataRoot);

        Room room = new Room(
            RoomId.of("moonlit-glade"),
            "Moonlit Glade",
            "Sunlight dapples the grass.",
            Map.of(),
            List.of(),
            List.of(),
            Map.of(),
            null,
            "Moonlight dapples the grass."
        );
        roomRepository.save(room);

        // Use a fresh repository instance so the read goes through the JSON file, not the cache.
        JsonRoomRepository reloadedRepository = new JsonRoomRepository(itemRepository, dataRoot);
        Optional<Room> loaded = reloadedRepository.findById(RoomId.of("moonlit-glade"));

        assertTrue(loaded.isPresent());
        assertEquals("Moonlight dapples the grass.", loaded.get().getNightDescription());
        assertEquals("Sunlight dapples the grass.", loaded.get().getDescription());
    }

    @Test
    void loadsLegacyRoomWithoutMinLevelAsNull() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        Path roomsDir = dataRoot.resolve("rooms");
        Files.createDirectories(roomsDir);
        Files.writeString(roomsDir.resolve("legacy.json"), """
            {
              "schema_version": 2,
              "id": "legacy",
              "name": "Legacy Room",
              "description": "An old room without a min level.",
              "item_ids": [],
              "exits": {}
            }
            """);

        JsonItemRepository itemRepository = new JsonItemRepository(dataRoot);
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, dataRoot);

        Optional<Room> loaded = roomRepository.findById(RoomId.of("legacy"));

        assertTrue(loaded.isPresent());
        assertNull(loaded.get().getMinLevel());
    }

    @Test
    void throwsOnInvalidJson() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        Path roomsDir = dataRoot.resolve("rooms");
        Files.createDirectories(roomsDir);
        Files.writeString(roomsDir.resolve("broken.json"), "{ not-valid-json");

        JsonItemRepository itemRepository = new JsonItemRepository(dataRoot);
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, dataRoot);

        RepositoryException error = assertThrows(
            RepositoryException.class,
            () -> roomRepository.findById(RoomId.of("broken"))
        );

        assertTrue(error.getMessage().contains("Failed to read room data"));
    }
}
