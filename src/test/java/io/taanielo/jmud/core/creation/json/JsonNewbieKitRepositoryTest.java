package io.taanielo.jmud.core.creation.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.creation.NewbieKit;
import io.taanielo.jmud.core.creation.NewbieKitException;
import io.taanielo.jmud.core.world.ItemId;

/**
 * Verifies that {@link JsonNewbieKitRepository} loads the seeded {@code data/newbie-kit.json}
 * definition, expands item quantities, and rejects malformed or unsupported data.
 */
class JsonNewbieKitRepositoryTest {

    @Test
    void load_loadsSeededKit() throws NewbieKitException {
        JsonNewbieKitRepository repo = new JsonNewbieKitRepository(Path.of("data"));

        NewbieKit kit = repo.load();

        assertTrue(kit.startingGold() > 0, "the shipped kit must grant some starting gold");
        assertTrue(kit.itemIds().contains(ItemId.of("bread")), "the shipped kit must include bread");
        assertTrue(kit.itemIds().contains(ItemId.of("water")), "the shipped kit must include water");
    }

    @Test
    void load_expandsItemQuantities(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("newbie-kit.json"), """
            {
              "schema_version": 1,
              "starting_gold": 25,
              "starting_items": [
                { "item": "bread", "quantity": 3 },
                { "item": "water" }
              ]
            }
            """);

        JsonNewbieKitRepository repo = new JsonNewbieKitRepository(tempDir);
        NewbieKit kit = repo.load();

        assertEquals(25, kit.startingGold());
        assertEquals(4, kit.itemIds().size(), "3 bread + 1 water flattened");
        assertEquals(3, kit.itemIds().stream().filter(id -> id.equals(ItemId.of("bread"))).count());
        assertEquals(1, kit.itemIds().stream().filter(id -> id.equals(ItemId.of("water"))).count());
    }

    @Test
    void load_rejectsUnsupportedSchemaVersion(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("newbie-kit.json"), """
            {
              "schema_version": 99,
              "starting_gold": 25,
              "starting_items": []
            }
            """);

        JsonNewbieKitRepository repo = new JsonNewbieKitRepository(tempDir);

        NewbieKitException ex = assertThrows(NewbieKitException.class, repo::load);
        assertTrue(ex.getMessage().contains("schema version"), ex.getMessage());
    }

    @Test
    void load_rejectsMissingFile(@TempDir Path tempDir) {
        JsonNewbieKitRepository repo = new JsonNewbieKitRepository(tempDir);

        NewbieKitException ex = assertThrows(NewbieKitException.class, repo::load);
        assertTrue(ex.getMessage().contains("not found"), ex.getMessage());
    }

    @Test
    void load_rejectsNegativeGold(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("newbie-kit.json"), """
            {
              "schema_version": 1,
              "starting_gold": -5,
              "starting_items": []
            }
            """);

        JsonNewbieKitRepository repo = new JsonNewbieKitRepository(tempDir);

        NewbieKitException ex = assertThrows(NewbieKitException.class, repo::load);
        assertTrue(ex.getMessage().contains("starting_gold"), ex.getMessage());
    }
}
