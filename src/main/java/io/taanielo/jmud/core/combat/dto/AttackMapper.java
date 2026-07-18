package io.taanielo.jmud.core.combat.dto;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackEffectApplication;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.DamageType;
import io.taanielo.jmud.core.combat.RangeType;
import io.taanielo.jmud.core.combat.WeaponType;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.messaging.MessageSpec;
import io.taanielo.jmud.core.messaging.MessageSpecMapper;

/**
 * Maps {@link AttackDto} instances to {@link AttackDefinition} domain objects.
 *
 * <p>Accepts schema versions 2 through 5. Schema version 2 omits {@code weapon_type};
 * the mapper defaults to {@link WeaponType#SLASHING} in that case. Schema version 4
 * additionally accepts an optional {@code applies_effect} field describing an on-hit
 * status effect application. Schema version 5 additionally accepts an optional
 * {@code range_type} field ({@code MELEE} or {@code RANGED}); it defaults to
 * {@link RangeType#MELEE} when absent or unrecognised. Schema version 6 additionally accepts an
 * optional {@code damage_type} field; it defaults to {@link DamageType#PHYSICAL} when absent or
 * unrecognised, so every pre-v6 attack file loads as physical damage unchanged. Schema version 7
 * additionally accepts an optional {@code telegraph_ticks} field; it defaults to {@code 0} (instant,
 * non-telegraphed resolution) when absent, so every pre-v7 attack file loads unchanged.
 */
public class AttackMapper {

    /**
     * Converts an {@link AttackDto} to its domain representation.
     *
     * @param dto the data-transfer object loaded from JSON; must not be {@code null}
     * @return the corresponding {@link AttackDefinition}
     * @throws IllegalArgumentException if the schema version is not supported
     */
    public AttackDefinition toDomain(AttackDto dto) {
        Objects.requireNonNull(dto, "Attack DTO is required");
        if (dto.schemaVersion() != AttackSchemaVersions.V2
            && dto.schemaVersion() != AttackSchemaVersions.V3
            && dto.schemaVersion() != AttackSchemaVersions.V4
            && dto.schemaVersion() != AttackSchemaVersions.V5
            && dto.schemaVersion() != AttackSchemaVersions.V6
            && dto.schemaVersion() != AttackSchemaVersions.V7) {
            throw new IllegalArgumentException("Unsupported attack schema version " + dto.schemaVersion());
        }
        List<MessageSpec> messages = MessageSpecMapper.fromDtos(dto.messages());
        WeaponType weaponType = parseWeaponType(dto.weaponType());
        AttackEffectApplication effectOnHit = parseEffectOnHit(dto.appliesEffect());
        RangeType rangeType = parseRangeType(dto.rangeType());
        DamageType damageType = DamageType.fromString(dto.damageType());
        int telegraphTicks = dto.telegraphTicks() == null ? 0 : Math.max(0, dto.telegraphTicks());
        return new AttackDefinition(
            AttackId.of(dto.id()),
            dto.name(),
            dto.minDamage(),
            dto.maxDamage(),
            dto.hitBonus(),
            dto.critBonus(),
            dto.damageBonus(),
            messages,
            weaponType,
            effectOnHit,
            rangeType,
            damageType,
            telegraphTicks
        );
    }

    private RangeType parseRangeType(String raw) {
        if (raw == null || raw.isBlank()) {
            return RangeType.MELEE;
        }
        try {
            return RangeType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RangeType.MELEE;
        }
    }

    private AttackEffectApplication parseEffectOnHit(AttackDto.AppliesEffectDto dto) {
        if (dto == null || dto.effectId() == null || dto.effectId().isBlank()) {
            return null;
        }
        return new AttackEffectApplication(EffectId.of(dto.effectId()), dto.chancePercent());
    }

    private WeaponType parseWeaponType(String raw) {
        if (raw == null || raw.isBlank()) {
            return WeaponType.SLASHING;
        }
        try {
            return WeaponType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return WeaponType.SLASHING;
        }
    }
}
