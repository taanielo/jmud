package io.taanielo.jmud.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.character.repository.ClassRepositoryException;
import io.taanielo.jmud.core.character.repository.json.JsonClassRepository;

/**
 * Verifies that {@link JsonClassRepository#findAll()} loads all classes from
 * the {@code data/classes/} directory.
 */
class JsonClassRepositoryFindAllTest {

    @Test
    void findAll_returnsAllThreeClasses() throws ClassRepositoryException {
        JsonClassRepository repo = new JsonClassRepository(Path.of("data"));

        List<ClassDefinition> classes = repo.findAll();

        assertFalse(classes.isEmpty(), "Expected at least one class");
        Set<String> ids = classes.stream()
            .map(c -> c.id().getValue())
            .collect(Collectors.toSet());

        assertTrue(ids.contains("warrior"), "Expected 'warrior' class");
        assertTrue(ids.contains("mage"), "Expected 'mage' class");
        assertTrue(ids.contains("adventurer"), "Expected 'adventurer' class");
        assertEquals(3, classes.size(), "Expected exactly 3 classes");
    }

    @Test
    void findAll_returnsEmptyForEmptyDir() throws ClassRepositoryException {
        JsonClassRepository repo = new JsonClassRepository(Path.of("data-nonexistent-xyz"));
        List<ClassDefinition> classes = repo.findAll();
        assertTrue(classes.isEmpty());
    }
}
