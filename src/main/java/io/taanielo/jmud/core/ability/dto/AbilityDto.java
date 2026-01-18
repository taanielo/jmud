package io.taanielo.jmud.core.ability.dto;

import java.util.List;

import io.taanielo.jmud.core.ability.AbilityTargeting;
import io.taanielo.jmud.core.ability.AbilityType;

public record AbilityDto(
    int schemaVersion,
    String id,
    String name,
    AbilityType type,
    int level,
    AbilityCostDto cost,
    AbilityCooldownDto cooldown,
    AbilityTargeting targeting,
    List<String> aliases,
    List<AbilityEffectDto> effects
) {
}
