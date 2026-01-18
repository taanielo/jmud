package io.taanielo.jmud.core.character.dto;

public record RaceDto(
    int schemaVersion,
    String id,
    String name,
    RaceHealingDto healing
) {
}
