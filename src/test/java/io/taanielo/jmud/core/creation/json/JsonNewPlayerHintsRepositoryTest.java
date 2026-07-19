package io.taanielo.jmud.core.creation.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.creation.NewPlayerHints;
import io.taanielo.jmud.core.creation.NewPlayerHintsException;

/**
 * Verifies that {@link JsonNewPlayerHintsRepository} loads the seeded
 * {@code data/new-player-hints.json} block and rejects malformed or unsupported data.
 */
class JsonNewPlayerHintsRepositoryTest {

    @Test
    void load_loadsSeededHintsTeachingSurvivalCommands() throws NewPlayerHintsException {
        JsonNewPlayerHintsRepository repo = new JsonNewPlayerHintsRepository(Path.of("data"));

        NewPlayerHints hints = repo.load();

        assertTrue(hints.hasLines(), "the shipped hints must define at least one line");
        String joined = String.join("\n", hints.lines());
        assertTrue(joined.contains("CONSIDER"), "hints must teach CONSIDER: " + joined);
        assertTrue(joined.contains("EQUIP") || joined.contains("WIELD"), "hints must teach EQUIP/WIELD: " + joined);
        assertTrue(joined.contains("FLEE"), "hints must teach FLEE: " + joined);
        assertTrue(joined.contains("HELP"), "hints must teach HELP: " + joined);
    }

    @Test
    void load_readsTitleAndLines(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("new-player-hints.json"), """
            {
              "schema_version": 1,
              "title": "Getting Started",
              "lines": [ "first line", "second line" ]
            }
            """);

        JsonNewPlayerHintsRepository repo = new JsonNewPlayerHintsRepository(tempDir);
        NewPlayerHints hints = repo.load();

        assertEquals("Getting Started", hints.title());
        assertEquals(2, hints.lines().size());
        assertEquals("first line", hints.lines().get(0));
    }

    @Test
    void load_rejectsUnsupportedSchemaVersion(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("new-player-hints.json"), """
            {
              "schema_version": 99,
              "title": "Getting Started",
              "lines": [ "a line" ]
            }
            """);

        JsonNewPlayerHintsRepository repo = new JsonNewPlayerHintsRepository(tempDir);

        NewPlayerHintsException ex = assertThrows(NewPlayerHintsException.class, repo::load);
        assertTrue(ex.getMessage().contains("schema version"), ex.getMessage());
    }

    @Test
    void load_rejectsMissingFile(@TempDir Path tempDir) {
        JsonNewPlayerHintsRepository repo = new JsonNewPlayerHintsRepository(tempDir);

        NewPlayerHintsException ex = assertThrows(NewPlayerHintsException.class, repo::load);
        assertTrue(ex.getMessage().contains("not found"), ex.getMessage());
    }

    @Test
    void load_rejectsBlankTitle(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("new-player-hints.json"), """
            {
              "schema_version": 1,
              "title": "  ",
              "lines": [ "a line" ]
            }
            """);

        JsonNewPlayerHintsRepository repo = new JsonNewPlayerHintsRepository(tempDir);

        NewPlayerHintsException ex = assertThrows(NewPlayerHintsException.class, repo::load);
        assertTrue(ex.getMessage().contains("title"), ex.getMessage());
    }

    @Test
    void load_rejectsEmptyLines(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("new-player-hints.json"), """
            {
              "schema_version": 1,
              "title": "Getting Started",
              "lines": []
            }
            """);

        JsonNewPlayerHintsRepository repo = new JsonNewPlayerHintsRepository(tempDir);

        NewPlayerHintsException ex = assertThrows(NewPlayerHintsException.class, repo::load);
        assertTrue(ex.getMessage().contains("at least one line"), ex.getMessage());
    }

    @Test
    void load_rejectsBlankLine(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("new-player-hints.json"), """
            {
              "schema_version": 1,
              "title": "Getting Started",
              "lines": [ "ok", "  " ]
            }
            """);

        JsonNewPlayerHintsRepository repo = new JsonNewPlayerHintsRepository(tempDir);

        NewPlayerHintsException ex = assertThrows(NewPlayerHintsException.class, repo::load);
        assertTrue(ex.getMessage().contains("blank line"), ex.getMessage());
    }
}
