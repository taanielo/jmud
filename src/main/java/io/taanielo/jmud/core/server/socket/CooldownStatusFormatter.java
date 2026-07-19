package io.taanielo.jmud.core.server.socket;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.tick.system.CooldownSystem;

/**
 * Formats the live readiness status of an ability for the {@code COOLDOWNS} command.
 *
 * <p>The status is read directly from a player's {@link CooldownSystem}, so it reflects the
 * ability's actual current readiness rather than its static base cooldown length. This is a
 * pure, read-only formatter: it never mutates cooldown state.
 */
final class CooldownStatusFormatter {

    private CooldownStatusFormatter() {
    }

    /**
     * Formats the current readiness of the given ability.
     *
     * @param cooldowns the player's cooldown system, keyed by {@link AbilityId#getValue()}
     * @param abilityId the ability whose readiness to format
     * @return {@code "Ready"} when the ability is off cooldown, or {@code "<n> ticks"} giving the
     *         number of ticks remaining until it becomes ready again
     */
    static String format(CooldownSystem cooldowns, AbilityId abilityId) {
        String key = abilityId.getValue();
        if (cooldowns.isOnCooldown(key)) {
            return cooldowns.remainingTicks(key) + " ticks";
        }
        return "Ready";
    }
}
