package io.taanielo.jmud.core.character.dto;

public record ClassDto(
    int schemaVersion,
    String id,
    String name,
    ClassHealingDto healing,
    int carryBonus
) {
}
