package io.taanielo.jmud.core.world.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.RepositoryException;

class JsonItemRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsItem() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository repository = new JsonItemRepository(dataRoot);

        Item item = new Item(
            ItemId.of("rusty-dagger"),
            "Rusty Dagger",
            "A dull blade with a chipped edge.",
            ItemAttributes.empty(),
            List.of(),
            List.of(),
            null,
            2,
            3,
            null
        );
        repository.save(item);

        Optional<Item> loaded = repository.findById(ItemId.of("rusty-dagger"));

        assertTrue(loaded.isPresent());
        assertEquals(item, loaded.get());
    }

    @Test
    void savesAndLoadsContainerWithContents() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository repository = new JsonItemRepository(dataRoot);

        Item apple = new Item(
            ItemId.of("apple"),
            "an apple",
            "A crisp red apple.",
            ItemAttributes.empty(),
            List.of(),
            List.of(),
            null,
            1,
            2,
            null
        );
        Item bag = new Item(
            ItemId.of("leather-bag"),
            "a leather bag",
            "A supple leather bag.",
            ItemAttributes.empty(),
            List.of(),
            List.of(),
            null,
            1,
            20,
            null,
            null,
            5,
            List.of(apple)
        );
        repository.save(bag);

        // Fresh repository (no cache) so the values come back off disk.
        Optional<Item> loaded = new JsonItemRepository(dataRoot).findById(ItemId.of("leather-bag"));

        assertTrue(loaded.isPresent());
        Item reloaded = loaded.get();
        assertTrue(reloaded.isContainer());
        assertEquals(Integer.valueOf(5), reloaded.getContainerCapacity());
        assertEquals(1, reloaded.containedItemCount());
        assertEquals(ItemId.of("apple"), reloaded.getContainedItems().getFirst().getId());
        assertEquals(bag, reloaded);
    }

    @Test
    void throwsOnInvalidJson() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        Path itemsDir = dataRoot.resolve("items");
        Files.createDirectories(itemsDir);
        Files.writeString(itemsDir.resolve("broken.json"), "{ not-valid-json");

        JsonItemRepository repository = new JsonItemRepository(dataRoot);

        RepositoryException error = assertThrows(
            RepositoryException.class,
            () -> repository.findById(ItemId.of("broken"))
        );

        assertTrue(error.getMessage().contains("Failed to read item data"));
    }
}
