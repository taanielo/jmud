package io.taanielo.jmud.core.world.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.world.AffixId;
import io.taanielo.jmud.core.world.ItemAffix;
import io.taanielo.jmud.core.world.Rarity;
import io.taanielo.jmud.core.world.repository.RepositoryException;

class JsonAffixRepositoryTest {

    @TempDir
    Path tempDir;

    private static final String VALID = """
        {
          "schema_version": 1,
          "affixes": [
            {
              "id": "of-the-bear",
              "label": "of the Bear",
              "stats": { "strength": 2 },
              "allowed_rarities": ["uncommon", "rare"]
            }
          ]
        }
        """;

    @Test
    void loadsAffixDefinition() throws Exception {
        Files.writeString(tempDir.resolve("item-affixes.json"), VALID);
        JsonAffixRepository repository = new JsonAffixRepository(tempDir);

        Optional<ItemAffix> affix = repository.findById(AffixId.of("of-the-bear"));

        assertTrue(affix.isPresent());
        assertEquals("of the Bear", affix.get().label());
        assertEquals(Integer.valueOf(2), affix.get().stats().get("strength"));
        assertTrue(affix.get().allowsRarity(Rarity.RARE));
        assertFalse(affix.get().allowsRarity(Rarity.COMMON));
    }

    @Test
    void missingFileYieldsEmptyAffixSet() throws Exception {
        JsonAffixRepository repository = new JsonAffixRepository(tempDir);

        assertTrue(repository.findAll().isEmpty());
        assertTrue(repository.findById(AffixId.of("of-the-bear")).isEmpty());
    }

    @Test
    void unsupportedSchemaVersionIsRejected() throws Exception {
        Files.writeString(tempDir.resolve("item-affixes.json"),
            "{ \"schema_version\": 99, \"affixes\": [] }");
        JsonAffixRepository repository = new JsonAffixRepository(tempDir);

        RepositoryException error = assertThrows(RepositoryException.class, repository::findAll);
        assertTrue(error.getMessage().contains("Unsupported affix schema version"));
    }

    @Test
    void bundledAffixDataLoads() throws Exception {
        JsonAffixRepository repository = new JsonAffixRepository(Path.of("data"));

        assertFalse(repository.findAll().isEmpty(), "Bundled data/item-affixes.json should define affixes");
    }
}
