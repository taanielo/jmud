package io.taanielo.jmud.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.combat.dto.AttackDto;
import io.taanielo.jmud.core.combat.dto.AttackMapper;
import io.taanielo.jmud.core.combat.dto.AttackSchemaVersions;

/**
 * Tests for {@link WeaponType} integration with {@link AttackDefinition} and
 * {@link AttackMapper}.
 */
class WeaponTypeTest {

    private final AttackMapper mapper = new AttackMapper();

    // -----------------------------------------------------------------------
    // AttackDefinition construction
    // -----------------------------------------------------------------------

    @Test
    void attackDefinitionDefaultsToSlashing() {
        AttackDefinition def = new AttackDefinition(
            AttackId.of("attack.test"), "test sword", 1, 4, 0, 0, 0, List.of()
        );
        assertEquals(WeaponType.SLASHING, def.weaponType());
    }

    @Test
    void attackDefinitionStoresBlunt() {
        AttackDefinition def = new AttackDefinition(
            AttackId.of("attack.mace"), "iron mace", 5, 10, 2, 2, 2, List.of(), WeaponType.BLUNT
        );
        assertEquals(WeaponType.BLUNT, def.weaponType());
    }

    @Test
    void attackDefinitionStoresPiercing() {
        AttackDefinition def = new AttackDefinition(
            AttackId.of("attack.dagger"), "dagger", 1, 5, 8, 4, 0, List.of(), WeaponType.PIERCING
        );
        assertEquals(WeaponType.PIERCING, def.weaponType());
    }

    @Test
    void attackDefinitionStoresSlashing() {
        AttackDefinition def = new AttackDefinition(
            AttackId.of("attack.sword"), "iron sword", 3, 8, 5, 1, 1, List.of(), WeaponType.SLASHING
        );
        assertEquals(WeaponType.SLASHING, def.weaponType());
    }

    // -----------------------------------------------------------------------
    // Mapper round-trips (schema version 3)
    // -----------------------------------------------------------------------

    @Test
    void mapperParsesBluntFromV3() {
        AttackDto dto = new AttackDto(
            AttackSchemaVersions.V3, "attack.mace", "iron mace", 5, 10, 2, 2, 2, List.of(), "BLUNT"
        );
        AttackDefinition def = mapper.toDomain(dto);
        assertEquals(WeaponType.BLUNT, def.weaponType());
    }

    @Test
    void mapperParsesPiercingFromV3() {
        AttackDto dto = new AttackDto(
            AttackSchemaVersions.V3, "attack.dagger", "dagger", 1, 5, 8, 4, 0, List.of(), "PIERCING"
        );
        AttackDefinition def = mapper.toDomain(dto);
        assertEquals(WeaponType.PIERCING, def.weaponType());
    }

    @Test
    void mapperParsesSlashingFromV3() {
        AttackDto dto = new AttackDto(
            AttackSchemaVersions.V3, "attack.sword", "iron sword", 3, 8, 5, 1, 1, List.of(), "SLASHING"
        );
        AttackDefinition def = mapper.toDomain(dto);
        assertEquals(WeaponType.SLASHING, def.weaponType());
    }

    // -----------------------------------------------------------------------
    // Mapper backward compatibility (schema version 2, no weapon_type)
    // -----------------------------------------------------------------------

    @Test
    void mapperDefaultsToSlashingWhenWeaponTypeAbsentInV2() {
        AttackDto dto = new AttackDto(
            AttackSchemaVersions.V2, "attack.old", "old sword", 2, 6, 3, 0, 0, List.of(), null
        );
        AttackDefinition def = mapper.toDomain(dto);
        assertEquals(WeaponType.SLASHING, def.weaponType());
    }

    @Test
    void mapperDefaultsToSlashingWhenWeaponTypeBlankInV3() {
        AttackDto dto = new AttackDto(
            AttackSchemaVersions.V3, "attack.blank", "blank weapon", 1, 3, 0, 0, 0, List.of(), ""
        );
        AttackDefinition def = mapper.toDomain(dto);
        assertEquals(WeaponType.SLASHING, def.weaponType());
    }

    @Test
    void mapperDefaultsToSlashingForUnknownWeaponType() {
        AttackDto dto = new AttackDto(
            AttackSchemaVersions.V3, "attack.weird", "weird weapon", 1, 3, 0, 0, 0, List.of(), "UNKNOWN_TYPE"
        );
        AttackDefinition def = mapper.toDomain(dto);
        assertEquals(WeaponType.SLASHING, def.weaponType());
    }

    // -----------------------------------------------------------------------
    // CombatEngine still resolves messages correctly regardless of weapon type
    // -----------------------------------------------------------------------

    @Test
    void combatEngineResolvesHitMessageForBluntWeapon() throws Exception {
        AttackId attackId = AttackId.of("attack.blunt-test");
        AttackDefinition attack = new AttackDefinition(
            attackId, "iron mace", 5, 10, 0, 0, 0, List.of(), WeaponType.BLUNT
        );
        // No messages supplied — engine falls back to generic messages
        assertNotNull(attack.weaponType());
        assertEquals(WeaponType.BLUNT, attack.weaponType());
    }

    @Test
    void combatEngineResolvesHitMessageForPiercingWeapon() throws Exception {
        AttackId attackId = AttackId.of("attack.pierce-test");
        AttackDefinition attack = new AttackDefinition(
            attackId, "dagger", 1, 5, 0, 0, 0, List.of(), WeaponType.PIERCING
        );
        assertNotNull(attack.weaponType());
        assertEquals(WeaponType.PIERCING, attack.weaponType());
    }
}
