package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.tick.system.CooldownSystem;

/**
 * Unit tests for {@link CooldownStatusFormatter}, covering the live cooldown-status rendering
 * used by the {@code COOLDOWNS} command.
 */
class CooldownStatusFormatterTest {

    private static final AbilityId ABILITY = AbilityId.of("fireball");

    @Test
    void neverUsedAbilityIsReady() {
        CooldownSystem cooldowns = new CooldownSystem();

        assertEquals("Ready", CooldownStatusFormatter.format(cooldowns, ABILITY));
    }

    @Test
    void abilityJustPutOnCooldownShowsRemainingTicks() {
        CooldownSystem cooldowns = new CooldownSystem();
        cooldowns.register(ABILITY.getValue(), 3);

        assertEquals("3 ticks", CooldownStatusFormatter.format(cooldowns, ABILITY));
    }

    @Test
    void remainingTicksDecreaseEachTickAndFlipToReadyAtZero() {
        CooldownSystem cooldowns = new CooldownSystem();
        cooldowns.register(ABILITY.getValue(), 2);

        assertEquals("2 ticks", CooldownStatusFormatter.format(cooldowns, ABILITY));

        cooldowns.tick();
        assertEquals("1 ticks", CooldownStatusFormatter.format(cooldowns, ABILITY));

        cooldowns.tick();
        assertEquals("Ready", CooldownStatusFormatter.format(cooldowns, ABILITY));
    }

    @Test
    void abilityRegisteredWithZeroTicksIsReady() {
        CooldownSystem cooldowns = new CooldownSystem();
        cooldowns.register(ABILITY.getValue(), 0);

        assertEquals("Ready", CooldownStatusFormatter.format(cooldowns, ABILITY));
    }
}
