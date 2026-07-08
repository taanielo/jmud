package io.taanielo.jmud.core.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class AbilityDefinitionTest {

    @Test
    void allowsCommandOnlyAbilityWithoutEffects() {
        AbilityDefinition ability = new AbilityDefinition(
            AbilityId.of("skill.pick"),
            "pick",
            AbilityType.SKILL,
            1,
            new AbilityCost(0, 0),
            new AbilityCooldown(0),
            AbilityTargeting.NONE,
            List.of(),
            List.of(),
            List.of()
        );

        assertEquals(AbilityTargeting.NONE, ability.targeting());
        assertEquals(List.of(), ability.effects());
    }

    @Test
    void rejectsNonUtilityAbilityWithoutEffects() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            new AbilityDefinition(
                AbilityId.of("skill.bash"),
                "bash",
                AbilityType.SKILL,
                1,
                new AbilityCost(0, 3),
                new AbilityCooldown(3),
                AbilityTargeting.HARMFUL,
                List.of(),
                List.of(),
                List.of()
            ));

        assertEquals("Ability must define at least one effect", exception.getMessage());
    }
}
