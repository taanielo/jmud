package io.taanielo.jmud.core.world.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.reload.PreparedItemReload;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemAttributes;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.RepositoryException;

class JsonItemRepositoryReloadTest {

    @TempDir
    Path tempDir;

    private static Item sword(int value) {
        return Item.builder(ItemId.of("sword"), "Sword", "A sword.", ItemAttributes.empty())
            .weight(3)
            .value(value)
            .build();
    }

    @Test
    void preparePicksUpDiskChangesButCommitAppliesThemOnly() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository repository = new JsonItemRepository(dataRoot);
        repository.save(sword(10));
        assertEquals(10, repository.findById(ItemId.of("sword")).orElseThrow().getValue());

        // Simulate an external edit to the file that the live cache does not yet know about.
        new JsonItemRepository(dataRoot).save(sword(20));
        assertEquals(10, repository.findById(ItemId.of("sword")).orElseThrow().getValue());

        PreparedItemReload prepared = repository.prepareItems();
        assertEquals(1, prepared.count());
        assertEquals(20, prepared.find(ItemId.of("sword")).orElseThrow().getValue());
        // Prepare must not mutate live state.
        assertEquals(10, repository.findById(ItemId.of("sword")).orElseThrow().getValue());

        prepared.commit();
        assertEquals(20, repository.findById(ItemId.of("sword")).orElseThrow().getValue());
    }

    @Test
    void prepareThrowsOnBrokenFileAndLeavesCacheUnchanged() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository repository = new JsonItemRepository(dataRoot);
        repository.save(sword(10));

        Files.writeString(dataRoot.resolve("items").resolve("broken.json"), "{ not-valid-json");

        assertThrows(RepositoryException.class, repository::prepareItems);
        // Transactional: the live cache is untouched by a failed prepare.
        assertEquals(10, repository.findById(ItemId.of("sword")).orElseThrow().getValue());
    }

    @Test
    void reloadRemovesEntriesForDeletedFiles() throws Exception {
        Path dataRoot = tempDir.resolve("data");
        JsonItemRepository repository = new JsonItemRepository(dataRoot);
        repository.save(sword(10));
        Item shield = Item.builder(ItemId.of("shield"), "Shield", "A shield.", ItemAttributes.empty())
            .weight(6).value(15).build();
        repository.save(shield);

        Files.delete(dataRoot.resolve("items").resolve("shield.json"));

        repository.prepareItems().commit();

        assertTrue(repository.findById(ItemId.of("sword")).isPresent());
        assertTrue(repository.findById(ItemId.of("shield")).isEmpty());
    }
}
