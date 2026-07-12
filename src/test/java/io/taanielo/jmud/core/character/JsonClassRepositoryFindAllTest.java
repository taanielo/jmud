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
    void findAll_everyClassHasACreationDescription() throws ClassRepositoryException {
        JsonClassRepository repo = new JsonClassRepository(Path.of("data"));

        for (ClassDefinition classDefinition : repo.findAll()) {
            assertFalse(classDefinition.description().isBlank(),
                "Class '" + classDefinition.id().getValue() + "' must have a creation description");
        }
    }

    @Test
    void findAll_loadsTunedPerClassLevelGains() throws ClassRepositoryException {
        JsonClassRepository repo = new JsonClassRepository(Path.of("data"));

        ClassDefinition warrior = repo.findById(ClassId.of("warrior")).orElseThrow();
        ClassDefinition mage = repo.findById(ClassId.of("mage")).orElseThrow();

        // Warrior leans HP-heavy, mage leans mana-heavy — visibly different vitals profiles.
        assertEquals(new LevelGains(13, 2, 3), warrior.levelGains());
        assertEquals(new LevelGains(6, 9, 3), mage.levelGains());
        assertTrue(warrior.levelGains().hp() > mage.levelGains().hp(),
            "Warrior should gain more HP per level than a mage");
        assertTrue(mage.levelGains().mana() > warrior.levelGains().mana(),
            "Mage should gain more mana per level than a warrior");
    }

    @Test
    void findAll_everyClassHasComparableTotalLevelGains() throws ClassRepositoryException {
        JsonClassRepository repo = new JsonClassRepository(Path.of("data"));

        for (ClassDefinition classDefinition : repo.findAll()) {
            LevelGains gains = classDefinition.levelGains();
            int total = gains.hp() + gains.mana() + gains.move();
            // All classes are tuned to the same total power budget as the legacy default (10+5+3),
            // so no class is strictly stronger — only the distribution differs by archetype.
            assertEquals(18, total,
                "Class '" + classDefinition.id().getValue() + "' level-gain total should equal 18");
        }
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
