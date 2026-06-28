package io.taanielo.jmud.core.combat.dto;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.WeaponType;
import io.taanielo.jmud.core.messaging.MessageSpec;
import io.taanielo.jmud.core.messaging.MessageSpecMapper;

/**
 * Maps {@link AttackDto} instances to {@link AttackDefinition} domain objects.
 *
 * <p>Accepts both schema version 2 (no {@code weapon_type} field) and schema
 * version 3 (with optional {@code weapon_type} field).  When the field is absent
 * or unrecognised the weapon type defaults to {@link WeaponType#SLASHING}.
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
            && dto.schemaVersion() != AttackSchemaVersions.V3) {
            throw new IllegalArgumentException("Unsupported attack schema version " + dto.schemaVersion());
        }
        List<MessageSpec> messages = MessageSpecMapper.fromDtos(dto.messages());
        WeaponType weaponType = parseWeaponType(dto.weaponType());
        return new AttackDefinition(
            AttackId.of(dto.id()),
            dto.name(),
            dto.minDamage(),
            dto.maxDamage(),
            dto.hitBonus(),
            dto.critBonus(),
            dto.damageBonus(),
            messages,
            weaponType
        );
    }

    private WeaponType parseWeaponType(String raw) {
        if (raw == null || raw.isBlank()) {
            return WeaponType.SLASHING;
        }
        try {
            return WeaponType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return WeaponType.SLASHING;
        }
    }
}
