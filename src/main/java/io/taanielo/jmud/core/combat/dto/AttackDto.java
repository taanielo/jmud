package io.taanielo.jmud.core.combat.dto;

import java.util.List;

import io.taanielo.jmud.core.messaging.dto.MessageSpecDto;

public record AttackDto(
    int schemaVersion,
    String id,
    String name,
    int minDamage,
    int maxDamage,
    int hitBonus,
    int critBonus,
    int damageBonus,
    List<MessageSpecDto> messages
) {
}
