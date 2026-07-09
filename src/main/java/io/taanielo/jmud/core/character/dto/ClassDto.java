package io.taanielo.jmud.core.character.dto;

import java.util.List;

public record ClassDto(
    int schemaVersion,
    String id,
    String name,
    ClassHealingDto healing,
    int carryBonus,
    int armorBonus,
    List<String> abilityIds
) {
}
