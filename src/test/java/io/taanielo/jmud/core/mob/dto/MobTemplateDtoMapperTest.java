package io.taanielo.jmud.core.mob.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.combat.DamageType;
import io.taanielo.jmud.core.mob.MobTemplate;

class MobTemplateDtoMapperTest {

    private final MobTemplateDtoMapper mapper = new MobTemplateDtoMapper();

    @Test
    void worldEventFlagIsMapped() {
        MobTemplateDto absent = new MobTemplateDto(
            4, "test-mob", "Test Mob", 10, null, null, false, null, null, "room-1",
            1, 10, 5, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null);
        assertFalse(mapper.toDomain(absent).worldEvent(), "world_event defaults to false when absent");

        MobTemplateDto present = new MobTemplateDto(
            4, "event-mob", "Event Mob", 10, null, null, false, null, null, "room-1",
            1, 10, 5, null, null, null, null, null, null, null, null, true, null, null, null,
            null, null, null, null, null, null, null, null, null);
        assertTrue(mapper.toDomain(present).worldEvent(), "world_event=true maps to a world-event template");
    }

    @Test
    void parryChanceDefaultsToZeroWhenAbsentAndIsMappedWhenPresent() {
        MobTemplateDto absent = new MobTemplateDto(
            4, "test-mob", "Test Mob", 10, null, null, false, null, null, "room-1",
            1, 10, 5, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null);
        assertEquals(0, mapper.toDomain(absent).parryChancePercent(),
            "parry_chance defaults to 0 when absent, so existing mob data never parries");
        assertFalse(mapper.toDomain(absent).canParry(), "a mob with no parry_chance cannot parry");

        MobTemplateDto present = new MobTemplateDto(
            4, "guard", "Town Guard", 10, null, null, false, null, null, "room-1",
            1, 10, 5, null, null, null, null, null, null, null, null, null, 20, null, null,
            null, null, null, null, null, null, null, null, null);
        assertEquals(20, mapper.toDomain(present).parryChancePercent(),
            "parry_chance=20 maps onto the template's parry chance");
        assertTrue(mapper.toDomain(present).canParry(), "a mob with a positive parry_chance can parry");
    }

    @Test
    void missingXpRewardFailsWithActionableMessage() {
        MobTemplateDto dto = new MobTemplateDto(
            4, "test-mob", "Test Mob", 10, null, null, false, null, null, "room-1",
            1, 10, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null);

        IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(dto));

        assertTrue(ex.getMessage().contains("xp_reward"),
            "message should name the missing field, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("test-mob"),
            "message should name the offending mob, got: " + ex.getMessage());
    }

    @Test
    void explicitXpRewardIsHonoured() {
        MobTemplateDto dto = new MobTemplateDto(
            4, "test-mob", "Test Mob", 10, null, null, false, null, null, "room-1",
            1, 10, 42, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null);

        assertEquals(42, mapper.toDomain(dto).xpReward());
    }

    @Test
    void resistancesAndVulnerabilitiesDefaultToEmptyWhenAbsent() {
        MobTemplateDto dto = new MobTemplateDto(
            4, "test-mob", "Test Mob", 10, null, null, false, null, null, "room-1",
            1, 10, 5, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null);
        MobTemplate template = mapper.toDomain(dto);
        assertEquals(0, template.resistancePercent(DamageType.FIRE),
            "an absent resistances object leaves the mob resisting nothing");
        assertEquals(0, template.vulnerabilityPercent(DamageType.COLD),
            "an absent vulnerabilities object leaves the mob vulnerable to nothing");
    }

    @Test
    void resistancesAndVulnerabilitiesAreParsedCaseInsensitively() {
        MobTemplateDto dto = new MobTemplateDto(
            4, "ice-mob", "Ice Mob", 10, null, null, false, null, null, "room-1",
            1, 10, 5, null, null, null, null, null, null, null, null, null, null,
            Map.of("cold", 50), Map.of("FIRE", 50), null, null, null, null, null, null, null, null, null);
        MobTemplate template = mapper.toDomain(dto);
        assertEquals(50, template.resistancePercent(DamageType.COLD),
            "cold resistance is parsed case-insensitively onto DamageType.COLD");
        assertEquals(50, template.vulnerabilityPercent(DamageType.FIRE),
            "fire vulnerability is parsed case-insensitively onto DamageType.FIRE");
    }

    @Test
    void healerFlagMapsToAHealerProfileWithDefaultThreshold() {
        MobTemplateDto dto = new MobTemplateDto(
            4, "medic", "Bandit Medic", 45, null, null, false, null, null, "room-1",
            1, 10, 5, null, null, null, null, null, null, null, null, null, null, null, null,
            true, 8, 16, null, null, null, null, null, null);
        MobTemplate template = mapper.toDomain(dto);
        assertTrue(template.isHealer(), "healer=true maps to a healer template");
        assertEquals(8, template.healerProfile().healMin());
        assertEquals(16, template.healerProfile().healMax());
        assertEquals(50, template.healerProfile().thresholdPercent(),
            "heal_threshold defaults to 50 when omitted");
    }

    @Test
    void absentHealerFlagLeavesOrdinaryMob() {
        MobTemplateDto dto = new MobTemplateDto(
            4, "test-mob", "Test Mob", 10, null, null, false, null, null, "room-1",
            1, 10, 5, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null);
        assertFalse(mapper.toDomain(dto).isHealer(),
            "healer defaults to false when absent, so existing mob data never heals");
    }

    @Test
    void healerWithoutHealAmountsIsRejected() {
        MobTemplateDto dto = new MobTemplateDto(
            4, "broken-medic", "Broken Medic", 45, null, null, false, null, null, "room-1",
            1, 10, 5, null, null, null, null, null, null, null, null, null, null, null, null,
            true, null, null, null, null, null, null, null, null);
        IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(dto));
        assertTrue(ex.getMessage().contains("heal_min"),
            "message should name the missing heal fields, got: " + ex.getMessage());
    }

    @Test
    void unknownDamageTypeKeyIsRejected() {
        MobTemplateDto dto = new MobTemplateDto(
            4, "bad-mob", "Bad Mob", 10, null, null, false, null, null, "room-1",
            1, 10, 5, null, null, null, null, null, null, null, null, null, null,
            Map.of("lightning", 50), null, null, null, null, null, null, null, null, null, null);
        IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(dto));
        assertTrue(ex.getMessage().contains("lightning"),
            "message should name the offending damage type, got: " + ex.getMessage());
    }

    @Test
    void enrageFieldsDefaultToNeverEnragesWhenAbsent() {
        MobTemplateDto dto = new MobTemplateDto(
            4, "test-mob", "Test Mob", 10, null, null, false, null, null, "room-1",
            1, 10, 5, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null);
        MobTemplate template = mapper.toDomain(dto);
        assertFalse(template.enrageCapable(),
            "an absent enrage_ticks leaves the mob never enraging, so existing mob data is unaffected");
        assertEquals(1.0, template.enrageDamageMultiplier(),
            "enrage_damage_multiplier defaults to 1.0 (no boost) when absent");
    }

    @Test
    void enrageFieldsAreMappedWhenPresent() {
        MobTemplateDto dto = new MobTemplateDto(
            4, "boss", "the Boss", 500, null, null, true, null, null, "room-1",
            1, 10, 5, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, 25, 1.5, null, null, null);
        MobTemplate template = mapper.toDomain(dto);
        assertTrue(template.enrageCapable(), "enrage_ticks present marks the mob enrage-capable");
        assertEquals(25, template.enrageTicks());
        assertEquals(1.5, template.enrageDamageMultiplier());
    }

    @Test
    void nonPositiveEnrageTicksIsRejected() {
        MobTemplateDto dto = new MobTemplateDto(
            4, "boss", "the Boss", 500, null, null, true, null, null, "room-1",
            1, 10, 5, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, 0, 1.5, null, null, null);
        IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(dto));
        assertTrue(ex.getMessage().contains("enrageTicks"),
            "message should name the offending field, got: " + ex.getMessage());
    }

    @Test
    void enrageMultiplierNotAboveOneIsRejected() {
        MobTemplateDto dto = new MobTemplateDto(
            4, "boss", "the Boss", 500, null, null, true, null, null, "room-1",
            1, 10, 5, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, 25, 1.0, null, null, null);
        IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(dto));
        assertTrue(ex.getMessage().contains("enrageDamageMultiplier"),
            "message should name the offending field, got: " + ex.getMessage());
    }
}
