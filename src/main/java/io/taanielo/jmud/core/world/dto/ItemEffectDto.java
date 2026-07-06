package io.taanielo.jmud.core.world.dto;

import io.taanielo.jmud.core.world.ItemEffectOperation;

public record ItemEffectDto(String effectId, int durationTicks, ItemEffectOperation op) {
}
