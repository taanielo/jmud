package io.taanielo.jmud.core.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.dto.AbilityCooldownDto;
import io.taanielo.jmud.core.ability.dto.AbilityCostDto;
import io.taanielo.jmud.core.ability.dto.AbilityDto;
import io.taanielo.jmud.core.ability.dto.AbilityEffectDto;
import io.taanielo.jmud.core.ability.dto.AbilityMapper;

/**
 * Verifies the additive optional {@code cast_time_ticks} field (issue #693): it defaults to 0
 * (instant, unchanged behaviour) when absent from ability JSON, and round-trips through the mapper
 * when present.
 */
class AbilityCastTimeTest {

    private final AbilityMapper mapper = new AbilityMapper();

    @Test
    void legacyConstructorDefaultsToInstant() {
        AbilityDefinition ability = new AbilityDefinition(
            AbilityId.of("spell.fireball"),
            "fireball",
            AbilityType.SPELL,
            1,
            new AbilityCost(3, 0, 0),
            new AbilityCooldown(4),
            AbilityTargeting.HARMFUL,
            List.of(),
            List.of(new AbilityEffect(
                AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 6, null, null)),
            List.of());
        assertEquals(0, ability.castTimeTicks());
    }

    @Test
    void mapperDefaultsMissingCastTimeToZero() {
        AbilityDto dto = new AbilityDto(
            2,
            "spell.fireball",
            "fireball",
            AbilityType.SPELL,
            1,
            new AbilityCostDto(3, null, null),
            new AbilityCooldownDto(4),
            null,
            AbilityTargeting.HARMFUL,
            List.of(),
            List.of(new AbilityEffectDto(
                AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 6, null, "FIRE", null)),
            List.of());
        Ability ability = mapper.toDomain(dto);
        assertEquals(0, ability.castTimeTicks(), "absent cast_time_ticks must default to instant");
    }

    @Test
    void mapperReadsPositiveCastTime() {
        AbilityDto dto = new AbilityDto(
            2,
            "spell.hurricane",
            "hurricane",
            AbilityType.SPELL,
            60,
            new AbilityCostDto(13, null, 3),
            new AbilityCooldownDto(10),
            3,
            AbilityTargeting.AoE,
            List.of(),
            List.of(new AbilityEffectDto(
                AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 12, null, null, null)),
            List.of());
        Ability ability = mapper.toDomain(dto);
        assertEquals(3, ability.castTimeTicks());
    }
}
