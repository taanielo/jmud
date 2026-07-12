package io.taanielo.jmud.core.character.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.character.ClassDefinition;

/**
 * Unit tests for {@link ClassMapper}, focusing on backward compatibility with legacy class JSON
 * that predates the split of the single ability list into starting and trainable pools.
 */
class ClassMapperTest {

    private final ClassMapper mapper = new ClassMapper();

    @Test
    void legacyClassWithOnlyStartingAbilitiesLoadsWithEmptyTrainablePool() {
        // A pre-split class (v3) provided only ability_ids and no trainable_ability_ids at all.
        ClassDto legacy = new ClassDto(
            ClassSchemaVersions.V3,
            "warrior",
            "Warrior",
            new ClassHealingDto(3),
            20,
            0,
            List.of("skill.bash", "skill.rend"),
            null,
            null
        );

        ClassDefinition def = mapper.toDomain(legacy);

        assertEquals(List.of(AbilityId.of("skill.bash"), AbilityId.of("skill.rend")),
            def.startingAbilityIds());
        assertNotNull(def.trainableAbilityIds(), "Trainable pool must never be null");
        assertTrue(def.trainableAbilityIds().isEmpty(),
            "Legacy class without trainable_ability_ids must fall back to an empty trainable pool");
        assertEquals("", def.description());
    }

    @Test
    void currentClassKeepsBothPoolsDistinct() {
        ClassDto current = new ClassDto(
            ClassSchemaVersions.V5,
            "warrior",
            "Warrior",
            new ClassHealingDto(3),
            20,
            0,
            List.of("skill.bash", "skill.rend"),
            List.of("skill.second-wind", "skill.taunt"),
            "The archetypal front-line fighter."
        );

        ClassDefinition def = mapper.toDomain(current);

        assertEquals(List.of(AbilityId.of("skill.bash"), AbilityId.of("skill.rend")),
            def.startingAbilityIds());
        assertEquals(List.of(AbilityId.of("skill.second-wind"), AbilityId.of("skill.taunt")),
            def.trainableAbilityIds());
    }
}
