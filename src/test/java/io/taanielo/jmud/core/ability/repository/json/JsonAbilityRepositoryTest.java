package io.taanielo.jmud.core.ability.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityEffect;
import io.taanielo.jmud.core.ability.AbilityEffectKind;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityOperation;
import io.taanielo.jmud.core.ability.AbilityStat;
import io.taanielo.jmud.core.ability.AbilityTargeting;
import io.taanielo.jmud.core.ability.AbilityType;
import io.taanielo.jmud.core.ability.repository.AbilityRepositoryException;
import io.taanielo.jmud.core.effects.ControlType;
import io.taanielo.jmud.core.messaging.MessageChannel;
import io.taanielo.jmud.core.messaging.MessagePhase;
import io.taanielo.jmud.core.messaging.MessageSpec;

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
              \"schema_version\": 2,
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
              ],
              \"messages\": [
                {\"phase\": \"use\", \"channel\": \"self\", \"text\": \"You cast {ability} on {target}.\"},
                {\"phase\": \"use\", \"channel\": \"target\", \"text\": \"{source} casts {ability} on you.\"},
                {\"phase\": \"use\", \"channel\": \"room\", \"text\": \"{source} casts {ability} on {target}.\"}
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
        MessageSpec selfMessage = ability.messages().stream()
            .filter(spec -> spec.phase() == MessagePhase.USE && spec.channel() == MessageChannel.SELF)
            .findFirst()
            .orElseThrow();
        assertEquals("You cast {ability} on {target}.", selfMessage.text());
        assertEquals(AbilityEffectKind.VITALS, ability.effects().getFirst().kind());
        assertEquals(AbilityStat.HP, ability.effects().getFirst().stat());
        assertEquals(AbilityOperation.INCREASE, ability.effects().getFirst().operation());
        assertTrue(repository.findById(AbilityId.of("spell.heal")).isPresent());
    }

    @Test
    void loadsAoeSpellWithScaledManaCost() throws Exception {
        Path skillsDir = tempDir.resolve("skills");
        Files.createDirectories(skillsDir);
        Path abilityFile = skillsDir.resolve("spell.chain-lightning.json");
        Files.writeString(abilityFile, """
            {
              \"schema_version\": 2,
              \"id\": \"spell.chain-lightning\",
              \"name\": \"chain-lightning\",
              \"type\": \"SPELL\",
              \"level\": 1,
              \"cost\": {\"mana\": 4, \"mana_per_target\": 2},
              \"cooldown\": {\"ticks\": 4},
              \"targeting\": \"AoE\",
              \"effects\": [
                {\"kind\": \"VITALS\", \"stat\": \"HP\", \"operation\": \"DECREASE\", \"amount\": 5}
              ]
            }
            """);

        JsonAbilityRepository repository = new JsonAbilityRepository(tempDir);
        Ability ability = repository.findById(AbilityId.of("spell.chain-lightning")).orElseThrow();

        assertEquals(AbilityTargeting.AoE, ability.targeting());
        assertEquals(4, ability.cost().mana());
        assertEquals(2, ability.cost().manaPerTarget());
        assertEquals(10, ability.cost().totalMana(3), "base 4 + 2 per target * 3 targets = 10");
    }

    @Test
    void loadsCureEffectTargetingControlClassification() throws Exception {
        Path skillsDir = tempDir.resolve("skills");
        Files.createDirectories(skillsDir);
        Path abilityFile = skillsDir.resolve("spell.cleanse.json");
        Files.writeString(abilityFile, """
            {
              \"schema_version\": 2,
              \"id\": \"spell.cleanse\",
              \"name\": \"cleanse\",
              \"type\": \"SPELL\",
              \"level\": 45,
              \"cost\": {\"mana\": 12},
              \"cooldown\": {\"ticks\": 12},
              \"targeting\": \"BENEFICIAL_GROUP\",
              \"effects\": [
                {\"kind\": \"EFFECT\", \"effect_id\": \"purified\"},
                {\"kind\": \"CURE\", \"control\": \"ROOT\"},
                {\"kind\": \"CURE\", \"control\": \"SILENCE\"},
                {\"kind\": \"CURE\", \"control\": \"STUN\"}
              ]
            }
            """);

        JsonAbilityRepository repository = new JsonAbilityRepository(tempDir);
        Ability ability = repository.findById(AbilityId.of("spell.cleanse")).orElseThrow();

        List<AbilityEffect> cures = ability.effects().stream()
            .filter(effect -> effect.kind() == AbilityEffectKind.CURE)
            .toList();
        assertEquals(3, cures.size());
        assertTrue(cures.stream().allMatch(effect -> effect.effectId() == null));
        assertEquals(
            List.of(ControlType.ROOT, ControlType.SILENCE, ControlType.STUN),
            cures.stream().map(AbilityEffect::control).toList()
        );
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
