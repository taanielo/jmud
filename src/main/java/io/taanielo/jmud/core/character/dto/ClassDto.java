package io.taanielo.jmud.core.character.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

public record ClassDto(
    int schemaVersion,
    String id,
    String name,
    ClassHealingDto healing,
    int carryBonus,
    int armorBonus,
    List<String> abilityIds,
    List<String> trainableAbilityIds,
    String description,
    ClassLevelGainsDto levelGains,
    @Nullable AttributeBonusDto attributeBonuses,
    @Nullable AttributeGainsDto attributeGains
) {
}
