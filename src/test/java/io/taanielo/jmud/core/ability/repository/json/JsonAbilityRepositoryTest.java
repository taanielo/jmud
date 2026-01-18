package io.taanielo.jmud.core.ability.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityEffectKind;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityOperation;
import io.taanielo.jmud.core.ability.AbilityStat;
import io.taanielo.jmud.core.ability.AbilityTargeting;
import io.taanielo.jmud.core.ability.AbilityType;
import io.taanielo.jmud.core.ability.repository.AbilityRepositoryException;

class JsonAbilityRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsAbilityDefinitions() throws Exception {
        Path skillsDir = tempDir.resolve("skills");
        Files.createDirectories(skillsDir);
        Path abilityFile = skillsDir.resolve("spell.heal.json");
        Files.writeString(abilityFile, """
            {
              \"schema_version\": 1,
              \"id\": \"spell.heal\",
              \"name\": \"heal\",
              \"type\": \"SPELL\",
              \"level\": 1,
              \"cost\": {\"mana\": 4},
              \"cooldown\": {\"ticks\": 3},
              \"targeting\": \"BENEFICIAL\",
              \"aliases\": [\"healing\"],
              \"effects\": [
                {\"kind\": \"VITALS\", \"stat\": \"HP\", \"operation\": \"INCREASE\", \"amount\": 6}
              ]
            }
            """);

        JsonAbilityRepository repository = new JsonAbilityRepository(tempDir);
        List<Ability> abilities = repository.findAll();

        assertEquals(1, abilities.size());
        Ability ability = abilities.getFirst();
        assertEquals("spell.heal", ability.id().getValue());
        assertEquals(AbilityType.SPELL, ability.type());
        assertEquals(AbilityTargeting.BENEFICIAL, ability.targeting());
        assertEquals(1, ability.effects().size());
        assertEquals(AbilityEffectKind.VITALS, ability.effects().getFirst().kind());
        assertEquals(AbilityStat.HP, ability.effects().getFirst().stat());
        assertEquals(AbilityOperation.INCREASE, ability.effects().getFirst().operation());
        assertTrue(repository.findById(AbilityId.of("spell.heal")).isPresent());
    }

    @Test
    void rejectsUnknownSchemaVersion() throws Exception {
        Path skillsDir = tempDir.resolve("skills");
        Files.createDirectories(skillsDir);
        Path abilityFile = skillsDir.resolve("spell.bad.json");
        Files.writeString(abilityFile, """
            {
              \"schema_version\": 99,
              \"id\": \"spell.bad\",
              \"name\": \"bad\",
              \"type\": \"SPELL\",
              \"level\": 1,
              \"cost\": {\"mana\": 0},
              \"cooldown\": {\"ticks\": 0},
              \"targeting\": \"BENEFICIAL\",
              \"effects\": [
                {\"kind\": \"VITALS\", \"stat\": \"HP\", \"operation\": \"INCREASE\", \"amount\": 1}
              ]
            }
            """);

        JsonAbilityRepository repository = new JsonAbilityRepository(tempDir);

        try {
            repository.findAll();
        } catch (AbilityRepositoryException e) {
            assertTrue(e.getMessage().contains("Unsupported ability schema version"));
            return;
        }
        throw new AssertionError("Expected AbilityRepositoryException");
    }
}
