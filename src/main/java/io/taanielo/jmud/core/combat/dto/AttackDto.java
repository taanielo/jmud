package io.taanielo.jmud.core.combat.dto;

import java.util.List;

import io.taanielo.jmud.core.messaging.dto.MessageSpecDto;

/**
 * Data-transfer object for a single attack definition loaded from JSON.
 *
 * <p>Schema version 3 adds the optional {@code weapon_type} field.
 * Files at schema version 2 that omit {@code weapon_type} are still accepted;
 * the mapper defaults to {@code SLASHING} in that case.
 *
 * <p>Schema version 4 adds the optional {@code applies_effect} field, describing
 * a status effect this attack applies to its target on a successful hit.
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
    String weaponType,
    AppliesEffectDto appliesEffect
) {

    /**
     * Data-transfer object describing an on-hit status effect application.
     *
     * @param effectId      id of the effect to apply (matches an {@code EffectDefinition} id)
     * @param chancePercent whole-number chance (1-100 inclusive) that the effect is applied on hit
     */
    public record AppliesEffectDto(String effectId, int chancePercent) {
    }
}
