package io.taanielo.jmud.core.combat.dto;

public record AttackDto(
    int schemaVersion,
    String id,
    String name,
    int minDamage,
    int maxDamage,
    int hitBonus,
    int critBonus,
    int damageBonus
) {
}
