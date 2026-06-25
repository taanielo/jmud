package io.taanielo.jmud.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.character.repository.RaceRepositoryException;
import io.taanielo.jmud.core.character.repository.json.JsonRaceRepository;

/**
 * Verifies that {@link JsonRaceRepository#findAll()} loads all races from the
 * {@code data/races/} directory.
 */
class JsonRaceRepositoryFindAllTest {

    @Test
    void findAll_returnsAllThreeRaces() throws RaceRepositoryException {
        JsonRaceRepository repo = new JsonRaceRepository(Path.of("data"));

        List<Race> races = repo.findAll();

        assertFalse(races.isEmpty(), "Expected at least one race");
        Set<String> ids = races.stream()
            .map(r -> r.id().getValue())
            .collect(Collectors.toSet());

        assertTrue(ids.contains("human"), "Expected 'human' race");
        assertTrue(ids.contains("elf"), "Expected 'elf' race");
        assertTrue(ids.contains("troll"), "Expected 'troll' race");
        assertEquals(3, races.size(), "Expected exactly 3 races");
    }

    @Test
    void findAll_returnsEmptyForEmptyDir() throws RaceRepositoryException {
        // Requesting a non-existent data root must return empty rather than throw.
        JsonRaceRepository repo = new JsonRaceRepository(Path.of("data-nonexistent-xyz"));
        List<Race> races = repo.findAll();
        assertTrue(races.isEmpty());
    }
}
