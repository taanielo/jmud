package io.taanielo.jmud.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.character.repository.ClassRepositoryException;
import io.taanielo.jmud.core.character.repository.json.JsonClassRepository;

/**
 * Verifies that {@link ClassDefinition#startingAbilityIds()} is populated when
 * classes are loaded from the {@code data/classes/} directory.
 */
class ClassDefinitionStartingAbilitiesTest {

    @Test
    void warriorHasBash() throws ClassRepositoryException {
        JsonClassRepository repo = new JsonClassRepository(Path.of("data"));
        List<ClassDefinition> classes = repo.findAll();

        ClassDefinition warrior = findById(classes, "warrior");
        List<String> ids = abilityIdValues(warrior.startingAbilityIds());

        assertTrue(ids.contains("skill.bash"), "Warrior must have skill.bash");
        assertEquals(1, ids.size(), "Warrior should have exactly 1 starting ability");
    }

    @Test
    void mageHasFireballHealAndBuffSpells() throws ClassRepositoryException {
        JsonClassRepository repo = new JsonClassRepository(Path.of("data"));
        List<ClassDefinition> classes = repo.findAll();

        ClassDefinition mage = findById(classes, "mage");
        List<String> ids = abilityIdValues(mage.startingAbilityIds());

        assertTrue(ids.contains("spell.fireball"), "Mage must have spell.fireball");
        assertTrue(ids.contains("spell.heal"), "Mage must have spell.heal");
        assertTrue(ids.contains("spell.stoneskin"), "Mage must have spell.stoneskin");
        assertTrue(ids.contains("spell.haste"), "Mage must have spell.haste");
        assertEquals(4, ids.size(), "Mage should have exactly 4 starting abilities");
    }

    @Test
    void adventurerHasBashAndHeal() throws ClassRepositoryException {
        JsonClassRepository repo = new JsonClassRepository(Path.of("data"));
        List<ClassDefinition> classes = repo.findAll();

        ClassDefinition adventurer = findById(classes, "adventurer");
        List<String> ids = abilityIdValues(adventurer.startingAbilityIds());

        assertTrue(ids.contains("skill.bash"), "Adventurer must have skill.bash");
        assertTrue(ids.contains("spell.heal"), "Adventurer must have spell.heal");
        assertEquals(2, ids.size(), "Adventurer should have exactly 2 starting abilities");
    }

    @Test
    void classDefinitionWithNoAbilityIdsHasEmptyList() {
        ClassDefinition def = new ClassDefinition(ClassId.of("test"), "Test", 0, 0);
        assertFalse(def.startingAbilityIds() == null, "startingAbilityIds must never be null");
        assertTrue(def.startingAbilityIds().isEmpty(), "4-arg constructor should yield empty list");
    }

    @Test
    void classDefinitionWithAbilityIdsReturnsUnmodifiableList() {
        List<AbilityId> ids = List.of(AbilityId.of("skill.bash"));
        ClassDefinition def = new ClassDefinition(ClassId.of("test"), "Test", 0, 0, ids);
        assertEquals(1, def.startingAbilityIds().size());
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static ClassDefinition findById(List<ClassDefinition> classes, String id) {
        return classes.stream()
            .filter(c -> c.id().getValue().equals(id))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Class not found: " + id));
    }

    private static List<String> abilityIdValues(List<AbilityId> ids) {
        return ids.stream().map(AbilityId::getValue).collect(Collectors.toList());
    }
}
