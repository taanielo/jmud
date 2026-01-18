package io.taanielo.jmud.core.effects.dto;

public record EffectModifierDto(
    String stat,
    String op,
    int amount
) {
}
