package io.taanielo.jmud.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.character.repository.RaceRepositoryException;
import io.taanielo.jmud.core.character.repository.json.JsonRaceRepository;

/**
 * Verifies that {@link JsonRaceRepository#findAll()} loads all races from the
 * {@code data/races/} directory.
 */
class JsonRaceRepositoryFindAllTest {

    @Test
    void findAll_returnsAllFourRaces() throws RaceRepositoryException {
        JsonRaceRepository repo = new JsonRaceRepository(Path.of("data"));

        List<Race> races = repo.findAll();

        assertFalse(races.isEmpty(), "Expected at least one race");
        Set<String> ids = races.stream()
            .map(r -> r.id().getValue())
            .collect(Collectors.toSet());

        assertTrue(ids.contains("human"), "Expected 'human' race");
        assertTrue(ids.contains("elf"), "Expected 'elf' race");
        assertTrue(ids.contains("troll"), "Expected 'troll' race");
        assertTrue(ids.contains("dwarf"), "Expected 'dwarf' race");
        assertEquals(4, races.size(), "Expected exactly 4 races");
    }

    @Test
    void findAll_returnsEmptyForEmptyDir(@TempDir Path tempDir) throws RaceRepositoryException {
        // Requesting a data root with no race files must return empty rather than throw.
        // A fresh temp subdirectory guarantees this holds on every machine and in CI,
        // and keeps the repository working tree free of directories created by
        // JsonRaceRepository's constructor.
        JsonRaceRepository repo = new JsonRaceRepository(tempDir.resolve("missing-subdir"));
        List<Race> races = repo.findAll();
        assertTrue(races.isEmpty());
    }
}
