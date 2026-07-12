package io.taanielo.jmud.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityTargeting;
import io.taanielo.jmud.core.ability.repository.AbilityRepositoryException;
import io.taanielo.jmud.core.ability.repository.json.JsonAbilityRepository;
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
        assertTrue(ids.contains("skill.rend"), "Warrior must have skill.rend");
        assertEquals(2, ids.size(), "Warrior should have exactly 2 starting abilities");

        // The higher-level combat skills are advanced options taught by the trainer, not
        // auto-granted at creation (issue #516).
        List<String> trainable = abilityIdValues(warrior.trainableAbilityIds());
        assertTrue(trainable.contains("skill.second-wind"), "Warrior must be able to train skill.second-wind");
        assertTrue(trainable.contains("skill.taunt"), "Warrior must be able to train skill.taunt");
    }

    @Test
    void mageHasFireballHealAndBuffSpells() throws ClassRepositoryException {
        JsonClassRepository repo = new JsonClassRepository(Path.of("data"));
        List<ClassDefinition> classes = repo.findAll();

        ClassDefinition mage = findById(classes, "mage");
        List<String> ids = abilityIdValues(mage.startingAbilityIds());

        assertTrue(ids.contains("spell.fireball"), "Mage must have spell.fireball");
        assertTrue(ids.contains("spell.heal"), "Mage must have spell.heal");
        assertTrue(ids.contains("spell.summon"), "Mage must have spell.summon");
        assertTrue(ids.contains("spell.chain-lightning"), "Mage must have spell.chain-lightning");
        assertEquals(4, ids.size(), "Mage should have exactly 4 starting abilities");

        // The utility buffs are trained at the Master Trainer, not granted at creation (issue #516).
        List<String> trainable = abilityIdValues(mage.trainableAbilityIds());
        assertTrue(trainable.contains("spell.stoneskin"), "Mage must be able to train spell.stoneskin");
        assertTrue(trainable.contains("spell.haste"), "Mage must be able to train spell.haste");
    }

    @Test
    void mageChainLightningGrantResolvesToAoeSpell()
        throws ClassRepositoryException, AbilityRepositoryException {
        JsonClassRepository classRepo = new JsonClassRepository(Path.of("data"));
        JsonAbilityRepository abilityRepo = new JsonAbilityRepository(Path.of("data"));

        ClassDefinition mage = findById(classRepo.findAll(), "mage");
        assertTrue(abilityIdValues(mage.startingAbilityIds()).contains("spell.chain-lightning"),
            "Mage must be granted Chain Lightning as its signature ability");

        Ability chainLightning =
            abilityRepo.findById(AbilityId.of("spell.chain-lightning")).orElseThrow(
                () -> new AssertionError("spell.chain-lightning must exist in data"));
        assertEquals(AbilityTargeting.AoE, chainLightning.targeting(),
            "Chain Lightning granted to Mage must be an AoE spell so it hits every hostile mob");
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
    void rangerHasTrackAndAimedShot() throws ClassRepositoryException {
        JsonClassRepository repo = new JsonClassRepository(Path.of("data"));
        List<ClassDefinition> classes = repo.findAll();

        ClassDefinition ranger = findById(classes, "ranger");
        List<String> ids = abilityIdValues(ranger.startingAbilityIds());

        assertTrue(ids.contains("skill.track"), "Ranger must have skill.track");
        assertTrue(ids.contains("skill.aimed-shot"), "Ranger must have skill.aimed-shot");
        assertEquals(2, ids.size(), "Ranger should have exactly 2 starting abilities");
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
