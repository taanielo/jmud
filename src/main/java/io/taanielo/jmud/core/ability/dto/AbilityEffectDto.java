package io.taanielo.jmud.core.ability.dto;

import io.taanielo.jmud.core.ability.AbilityEffectKind;
import io.taanielo.jmud.core.ability.AbilityOperation;
import io.taanielo.jmud.core.ability.AbilityStat;

public record AbilityEffectDto(
    AbilityEffectKind kind,
    AbilityStat stat,
    AbilityOperation operation,
    Integer amount,
    String effectId
) {
}
