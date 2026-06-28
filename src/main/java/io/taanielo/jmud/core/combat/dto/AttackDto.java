package io.taanielo.jmud.core.combat.dto;

import java.util.List;

import io.taanielo.jmud.core.messaging.dto.MessageSpecDto;

/**
 * Data-transfer object for a single attack definition loaded from JSON.
 *
 * <p>Schema version 3 adds the optional {@code weapon_type} field.
 * Files at schema version 2 that omit {@code weapon_type} are still accepted;
 * the mapper defaults to {@code SLASHING} in that case.
 */
public record AttackDto(
    int schemaVersion,
    String id,
    String name,
    int minDamage,
    int maxDamage,
    int hitBonus,
    int critBonus,
    int damageBonus,
    List<MessageSpecDto> messages,
    String weaponType
) {
}
