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

import io.taanielo.jmud.core.character.repository.ClassRepositoryException;
import io.taanielo.jmud.core.character.repository.json.JsonClassRepository;

/**
 * Verifies that {@link JsonClassRepository#findAll()} loads all classes from
 * the {@code data/classes/} directory.
 */
class JsonClassRepositoryFindAllTest {

    @Test
    void findAll_returnsAllClasses() throws ClassRepositoryException {
        JsonClassRepository repo = new JsonClassRepository(Path.of("data"));

        List<ClassDefinition> classes = repo.findAll();

        assertFalse(classes.isEmpty(), "Expected at least one class");
        Set<String> ids = classes.stream()
            .map(c -> c.id().getValue())
            .collect(Collectors.toSet());

        assertTrue(ids.contains("warrior"), "Expected 'warrior' class");
        assertTrue(ids.contains("mage"), "Expected 'mage' class");
        assertTrue(ids.contains("adventurer"), "Expected 'adventurer' class");
        assertTrue(ids.contains("rogue"), "Expected 'rogue' class");
        assertTrue(ids.contains("cleric"), "Expected 'cleric' class");
        assertTrue(ids.contains("ranger"), "Expected 'ranger' class");
        assertTrue(ids.contains("paladin"), "Expected 'paladin' class");
        assertTrue(ids.contains("bard"), "Expected 'bard' class");
        assertTrue(ids.contains("druid"), "Expected 'druid' class");
        assertTrue(ids.contains("shaman"), "Expected 'shaman' class");
        assertTrue(ids.contains("necromancer"), "Expected 'necromancer' class");
        assertEquals(11, classes.size(), "Expected exactly 11 classes");
    }

    @Test
    void findAll_returnsEmptyForEmptyDir(@TempDir Path tempDir) throws ClassRepositoryException {
        // A fresh temp subdirectory guarantees the path has no class files on every
        // machine and in CI, and keeps the repository working tree free of
        // directories created by JsonClassRepository's constructor.
        JsonClassRepository repo = new JsonClassRepository(tempDir.resolve("missing-subdir"));
        List<ClassDefinition> classes = repo.findAll();
        assertTrue(classes.isEmpty());
    }
}
