package io.taanielo.jmud.core.mob.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MobTemplateDtoMapperTest {

    private final MobTemplateDtoMapper mapper = new MobTemplateDtoMapper();

    @Test
    void missingXpRewardFailsWithActionableMessage() {
        MobTemplateDto dto = new MobTemplateDto(
            4, "test-mob", "Test Mob", 10, null, null, false, null, null, "room-1",
            1, 10, null, null, null, null, null, null, null, null, null);

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
            1, 10, 42, null, null, null, null, null, null, null, null);

        assertEquals(42, mapper.toDomain(dto).xpReward());
    }
}
