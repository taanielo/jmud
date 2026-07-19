package io.taanielo.jmud.core.world.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.world.ItemSet;
import io.taanielo.jmud.core.world.ItemSetId;
import io.taanielo.jmud.core.world.repository.RepositoryException;

class JsonItemSetRepositoryTest {

    @Test
    void loadsShippedWayfarersLeathersSet() throws Exception {
        JsonItemSetRepository repository = new JsonItemSetRepository(Path.of("data"));

        Optional<ItemSet> set = repository.findById(ItemSetId.of("wayfarers-leathers"));

        assertTrue(set.isPresent(), "shipped item set must load");
        assertEquals(3, set.get().pieceCount());
        assertEquals(2, set.get().thresholds().size());
    }

    @Test
    void missingDirectoryYieldsEmptyCatalog(@TempDir Path tempDir) throws Exception {
        JsonItemSetRepository repository = new JsonItemSetRepository(tempDir);

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void rejectsUnsupportedSchemaVersion(@TempDir Path tempDir) throws Exception {
        Path setsDir = Files.createDirectories(tempDir.resolve("item-sets"));
        Files.writeString(setsDir.resolve("bad.json"), """
            {
              "schema_version": 99,
              "id": "bad-set",
              "name": "Bad Set",
              "pieces": ["a", "b"],
              "thresholds": [{ "pieces_required": 2, "stats": { "ac": 1 } }]
            }
            """);
        JsonItemSetRepository repository = new JsonItemSetRepository(tempDir);

        assertThrows(RepositoryException.class, repository::findAll);
    }

    @Test
    void rejectsPieceListWithFewerThanTwoPieces(@TempDir Path tempDir) throws Exception {
        Path setsDir = Files.createDirectories(tempDir.resolve("item-sets"));
        Files.writeString(setsDir.resolve("tiny.json"), """
            {
              "schema_version": 1,
              "id": "tiny-set",
              "name": "Tiny Set",
              "pieces": ["a"],
              "thresholds": [{ "pieces_required": 2, "stats": { "ac": 1 } }]
            }
            """);
        JsonItemSetRepository repository = new JsonItemSetRepository(tempDir);

        assertThrows(RepositoryException.class, repository::findAll);
    }
}
