package io.taanielo.jmud.core.world.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            12
        );
        Item potion = new Item(
            ItemId.of("minor-potion"),
            "Minor Potion",
            "A small vial for quick recovery.",
            ItemAttributes.empty(),
            List.of(),
            List.of(),
            8
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
