package io.taanielo.jmud.core.world.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.List;

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
            3
        );
        repository.save(item);

        Optional<Item> loaded = repository.findById(ItemId.of("rusty-dagger"));

        assertTrue(loaded.isPresent());
        assertEquals(item, loaded.get());
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
