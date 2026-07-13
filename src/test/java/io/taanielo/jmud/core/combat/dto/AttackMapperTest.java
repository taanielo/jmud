package io.taanielo.jmud.core.combat.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackEffectApplication;
import io.taanielo.jmud.core.combat.DamageType;
import io.taanielo.jmud.core.combat.RangeType;
import io.taanielo.jmud.core.effects.EffectId;

class AttackMapperTest {

    private final AttackMapper mapper = new AttackMapper();

    @Test
    void mapsAppliesEffectFromSchemaVersion4() {
        AttackDto dto = new AttackDto(
            AttackSchemaVersions.V4,
            "attack.spider",
            "spider fangs",
            2,
            6,
            -5,
            5,
            0,
            List.of(),
            "PIERCING",
            new AttackDto.AppliesEffectDto("poison", 40),
            null,
            null
        );

        AttackDefinition attack = mapper.toDomain(dto);

        AttackEffectApplication effectOnHit = attack.effectOnHit();
        assertEquals(EffectId.of("poison"), effectOnHit.effectId());
        assertEquals(40, effectOnHit.chancePercent());
    }

    @Test
    void appliesEffectIsNullWhenAbsent() {
        AttackDto dto = new AttackDto(
            AttackSchemaVersions.V3,
            "attack.dagger",
            "dagger",
            1,
            5,
            8,
            4,
            0,
            List.of(),
            "PIERCING",
            null,
            null,
            null
        );

        AttackDefinition attack = mapper.toDomain(dto);

        assertNull(attack.effectOnHit());
    }

    @Test
    void defaultsRangeTypeToMeleeWhenAbsent() {
        AttackDto dto = new AttackDto(
            AttackSchemaVersions.V4,
            "attack.dagger",
            "dagger",
            1,
            5,
            8,
            4,
            0,
            List.of(),
            "PIERCING",
            null,
            null,
            null
        );

        AttackDefinition attack = mapper.toDomain(dto);

        assertEquals(RangeType.MELEE, attack.rangeType());
        assertEquals(DamageType.PHYSICAL, attack.damageType());
    }

    @Test
    void mapsRangedFromSchemaVersion5() {
        AttackDto dto = new AttackDto(
            AttackSchemaVersions.V5,
            "attack.long-bow",
            "long bow",
            3,
            9,
            5,
            2,
            0,
            List.of(),
            "PIERCING",
            null,
            "RANGED",
            null
        );

        AttackDefinition attack = mapper.toDomain(dto);

        assertEquals(RangeType.RANGED, attack.rangeType());
    }

    @Test
    void mapsDamageTypeFromSchemaVersion6() {
        AttackDto dto = new AttackDto(
            AttackSchemaVersions.V6,
            "attack.ember-wyrm-breath",
            "Cinder Breath",
            36,
            55,
            4,
            4,
            9,
            List.of(),
            "BLUNT",
            null,
            null,
            "FIRE"
        );

        AttackDefinition attack = mapper.toDomain(dto);

        assertEquals(DamageType.FIRE, attack.damageType());
    }

    @Test
    void defaultsDamageTypeToPhysicalForUnknownValue() {
        AttackDto dto = new AttackDto(
            AttackSchemaVersions.V6,
            "attack.weird",
            "weird",
            1,
            2,
            0,
            0,
            0,
            List.of(),
            "SLASHING",
            null,
            null,
            "sonic"
        );

        AttackDefinition attack = mapper.toDomain(dto);

        assertEquals(DamageType.PHYSICAL, attack.damageType());
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        AttackDto dto = new AttackDto(
            99,
            "attack.bad",
            "bad",
            1,
            1,
            0,
            0,
            0,
            List.of(),
            null,
            null,
            null,
            null
        );

        assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(dto));
    }
}
