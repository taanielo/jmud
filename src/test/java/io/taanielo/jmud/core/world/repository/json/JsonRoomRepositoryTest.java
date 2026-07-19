package io.taanielo.jmud.core.world.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import io.taanielo.jmud.core.combat.DamageType;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomHazard;
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

        Item sword = Item.builder(
            ItemId.of("practice-sword"), "Practice Sword", "A blunted sword for sparring.", ItemAttributes.empty())
            .weight(3)
            .value(12)
            .build();
        Item potion = Item.builder(
            ItemId.of("minor-potion"), "Minor Potion", "A small vial for quick recovery.", ItemAttributes.empty())
            .weight(1)
            .value(8)
            .build();
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
    void savesAndLoadsRoomWithAmbientMessages() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository itemRepository = new JsonItemRepository(dataRoot);
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, dataRoot);

        Room room = new Room(
            RoomId.of("dripping-cave"),
            "Dripping Cave",
            "A damp cavern.",
            Map.of(),
            List.of(),
            List.of(),
            Map.of(),
            null,
            null,
            null,
            false,
            List.of("Water drips somewhere in the dark.", "A cold draft sighs past.")
        );
        roomRepository.save(room);

        // Use a fresh repository instance so the read goes through the JSON file, not the cache.
        JsonRoomRepository reloadedRepository = new JsonRoomRepository(itemRepository, dataRoot);
        Optional<Room> loaded = reloadedRepository.findById(RoomId.of("dripping-cave"));

        assertTrue(loaded.isPresent());
        assertEquals(
            List.of("Water drips somewhere in the dark.", "A cold draft sighs past."),
            loaded.get().getAmbientMessages());
        assertTrue(loaded.get().hasAmbientMessages());
    }

    @Test
    void savesAndLoadsRoomWithHiddenExits() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository itemRepository = new JsonItemRepository(dataRoot);
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, dataRoot);

        Room room = new Room(
            RoomId.of("secret-armory"),
            "Secret Armory",
            "A dim armory.",
            Map.of(Direction.SOUTH, RoomId.of("hall")),
            List.of(),
            List.of(),
            Map.of(),
            null,
            null,
            null,
            false,
            List.of(),
            Map.of(Direction.DOWN, RoomId.of("vault"))
        );
        roomRepository.save(room);

        // Use a fresh repository instance so the read goes through the JSON file, not the cache.
        JsonRoomRepository reloadedRepository = new JsonRoomRepository(itemRepository, dataRoot);
        Optional<Room> loaded = reloadedRepository.findById(RoomId.of("secret-armory"));

        assertTrue(loaded.isPresent());
        assertEquals(Map.of(Direction.DOWN, RoomId.of("vault")), loaded.get().getHiddenExits());
        assertTrue(loaded.get().hasHiddenExits());
        // A hidden exit stays out of the normal exit map.
        assertFalse(loaded.get().getExits().containsKey(Direction.DOWN));
    }

    @Test
    void savesAndLoadsRoomWithHazard() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository itemRepository = new JsonItemRepository(dataRoot);
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, dataRoot);

        RoomHazard hazard = new RoomHazard(DamageType.POISON, 4, 8, "Acrid gas burns your throat!");
        Room room = new Room(
            RoomId.of("gas-pocket"),
            "Gas Pocket",
            "A choking, gas-filled hollow.",
            Map.of(),
            List.of(),
            List.of(),
            Map.of(),
            null,
            null,
            null,
            false,
            List.of(),
            Map.of(),
            hazard
        );
        roomRepository.save(room);

        // Use a fresh repository instance so the read goes through the JSON file, not the cache.
        JsonRoomRepository reloadedRepository = new JsonRoomRepository(itemRepository, dataRoot);
        Optional<Room> loaded = reloadedRepository.findById(RoomId.of("gas-pocket"));

        assertTrue(loaded.isPresent());
        assertTrue(loaded.get().hasHazard());
        assertEquals(hazard, loaded.get().getHazard());
    }

    @Test
    void loadsLegacyRoomWithoutHazardAsNull() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        Path roomsDir = dataRoot.resolve("rooms");
        Files.createDirectories(roomsDir);
        Files.writeString(roomsDir.resolve("legacy-hazardless.json"), """
            {
              "schema_version": 2,
              "id": "legacy-hazardless",
              "name": "Legacy Room",
              "description": "An old room with no hazard.",
              "item_ids": [],
              "exits": {}
            }
            """);

        JsonItemRepository itemRepository = new JsonItemRepository(dataRoot);
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, dataRoot);

        Optional<Room> loaded = roomRepository.findById(RoomId.of("legacy-hazardless"));

        assertTrue(loaded.isPresent());
        assertFalse(loaded.get().hasHazard());
        assertNull(loaded.get().getHazard());
    }

    @Test
    void loadsRoomHazardFromV10Json() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        Path roomsDir = dataRoot.resolve("rooms");
        Files.createDirectories(roomsDir);
        Files.writeString(roomsDir.resolve("magma.json"), """
            {
              "schema_version": 10,
              "id": "magma",
              "name": "Magma Ledge",
              "description": "A searing ledge above molten rock.",
              "item_ids": [],
              "exits": {},
              "hazard": {
                "damage_type": "FIRE",
                "damage_min": 12,
                "damage_max": 20,
                "damage_message": "Scalding heat sears your skin!"
              }
            }
            """);

        JsonItemRepository itemRepository = new JsonItemRepository(dataRoot);
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, dataRoot);

        Optional<Room> loaded = roomRepository.findById(RoomId.of("magma"));

        assertTrue(loaded.isPresent());
        assertTrue(loaded.get().hasHazard());
        RoomHazard hazard = loaded.get().getHazard();
        assertEquals(DamageType.FIRE, hazard.damageType());
        assertEquals(12, hazard.damageMin());
        assertEquals(20, hazard.damageMax());
        assertEquals("Scalding heat sears your skin!", hazard.damageMessage());
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
