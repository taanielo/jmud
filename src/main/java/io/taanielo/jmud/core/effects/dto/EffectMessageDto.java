package io.taanielo.jmud.core.effects.dto;

public record EffectMessageDto(
    String applySelf,
    String applyRoom,
    String expireSelf,
    String expireRoom,
    String examine
) {
}
