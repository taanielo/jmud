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

import io.taanielo.jmud.core.reload.ItemLookup;
import io.taanielo.jmud.core.reload.PreparedReload;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.repository.RepositoryException;

class JsonRoomRepositoryReloadTest {

    @TempDir
    Path tempDir;

    private static Item gem() {
        return Item.builder(ItemId.of("gem"), "Gem", "A shiny gem.", ItemAttributes.empty())
            .weight(1).value(50).build();
    }

    private static Room room(String description, List<Item> items) {
        return new Room(RoomId.of("vault"), "Vault", description, Map.of(), items, List.of());
    }

    @Test
    void commitSwapsRoomCacheToDiskState() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository itemRepository = new JsonItemRepository(dataRoot);
        itemRepository.save(gem());
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, dataRoot);
        roomRepository.save(room("A quiet vault.", List.of(gem())));
        assertEquals("A quiet vault.", roomRepository.findById(RoomId.of("vault")).orElseThrow().getDescription());

        // External edit the live cache does not know about.
        new JsonRoomRepository(itemRepository, dataRoot).save(room("A ransacked vault!", List.of(gem())));
        assertEquals("A quiet vault.", roomRepository.findById(RoomId.of("vault")).orElseThrow().getDescription());

        PreparedReload prepared = roomRepository.prepareRooms(itemRepository::findById);
        assertEquals("rooms", prepared.contentType());
        assertEquals(1, prepared.count());
        // Prepare must not mutate live state.
        assertEquals("A quiet vault.", roomRepository.findById(RoomId.of("vault")).orElseThrow().getDescription());

        prepared.commit();
        assertEquals("A ransacked vault!", roomRepository.findById(RoomId.of("vault")).orElseThrow().getDescription());
    }

    @Test
    void resolvesItemsThroughProvidedLookup() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository itemRepository = new JsonItemRepository(dataRoot);
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, dataRoot);

        // Write a room that references an item the item repository does not know about.
        Files.writeString(dataRoot.resolve("rooms").resolve("vault.json"), """
            {
              "schema_version": 2,
              "id": "vault",
              "name": "Vault",
              "description": "A vault holding a new gem.",
              "item_ids": ["gem"],
              "exits": {}
            }
            """);

        Item preparedGem = gem();
        ItemLookup lookup = id -> id.equals(ItemId.of("gem")) ? Optional.of(preparedGem) : Optional.empty();

        PreparedReload prepared = roomRepository.prepareRooms(lookup);
        prepared.commit();

        Room loaded = roomRepository.findById(RoomId.of("vault")).orElseThrow();
        assertEquals(1, loaded.getItems().size());
        assertEquals(ItemId.of("gem"), loaded.getItems().getFirst().getId());
    }

    @Test
    void prepareThrowsOnBrokenFileAndLeavesCacheUnchanged() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository itemRepository = new JsonItemRepository(dataRoot);
        itemRepository.save(gem());
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, dataRoot);
        roomRepository.save(room("A quiet vault.", List.of(gem())));

        Files.writeString(dataRoot.resolve("rooms").resolve("broken.json"), "{ not-valid-json");

        assertThrows(RepositoryException.class, () -> roomRepository.prepareRooms(itemRepository::findById));
        assertEquals("A quiet vault.", roomRepository.findById(RoomId.of("vault")).orElseThrow().getDescription());
    }

    @Test
    void prepareThrowsWhenRoomReferencesMissingItem() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository itemRepository = new JsonItemRepository(dataRoot);
        JsonRoomRepository roomRepository = new JsonRoomRepository(itemRepository, dataRoot);
        Files.writeString(dataRoot.resolve("rooms").resolve("vault.json"), """
            {
              "schema_version": 2,
              "id": "vault",
              "name": "Vault",
              "description": "A vault referencing a ghost item.",
              "item_ids": ["ghost"],
              "exits": {}
            }
            """);

        ItemLookup emptyLookup = id -> Optional.empty();
        RepositoryException error =
            assertThrows(RepositoryException.class, () -> roomRepository.prepareRooms(emptyLookup));
        assertTrue(error.getMessage().contains("ghost"));
    }
}
