package io.taanielo.jmud.core.mob.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MobTemplateDtoMapperTest {

    private final MobTemplateDtoMapper mapper = new MobTemplateDtoMapper();

    @Test
    void worldEventFlagIsMapped() {
        MobTemplateDto absent = new MobTemplateDto(
            4, "test-mob", "Test Mob", 10, null, null, false, null, null, "room-1",
            1, 10, 5, null, null, null, null, null, null, null, null, null, null);
        assertFalse(mapper.toDomain(absent).worldEvent(), "world_event defaults to false when absent");

        MobTemplateDto present = new MobTemplateDto(
            4, "event-mob", "Event Mob", 10, null, null, false, null, null, "room-1",
            1, 10, 5, null, null, null, null, null, null, null, null, true, null);
        assertTrue(mapper.toDomain(present).worldEvent(), "world_event=true maps to a world-event template");
    }

    @Test
    void parryChanceDefaultsToZeroWhenAbsentAndIsMappedWhenPresent() {
        MobTemplateDto absent = new MobTemplateDto(
            4, "test-mob", "Test Mob", 10, null, null, false, null, null, "room-1",
            1, 10, 5, null, null, null, null, null, null, null, null, null, null);
        assertEquals(0, mapper.toDomain(absent).parryChancePercent(),
            "parry_chance defaults to 0 when absent, so existing mob data never parries");
        assertFalse(mapper.toDomain(absent).canParry(), "a mob with no parry_chance cannot parry");

        MobTemplateDto present = new MobTemplateDto(
            4, "guard", "Town Guard", 10, null, null, false, null, null, "room-1",
            1, 10, 5, null, null, null, null, null, null, null, null, null, 20);
        assertEquals(20, mapper.toDomain(present).parryChancePercent(),
            "parry_chance=20 maps onto the template's parry chance");
        assertTrue(mapper.toDomain(present).canParry(), "a mob with a positive parry_chance can parry");
    }

    @Test
    void missingXpRewardFailsWithActionableMessage() {
        MobTemplateDto dto = new MobTemplateDto(
            4, "test-mob", "Test Mob", 10, null, null, false, null, null, "room-1",
            1, 10, null, null, null, null, null, null, null, null, null, null, null);

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
            1, 10, 42, null, null, null, null, null, null, null, null, null, null);

        assertEquals(42, mapper.toDomain(dto).xpReward());
    }
}
