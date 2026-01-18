package io.taanielo.jmud.core.combat;

import io.taanielo.jmud.core.config.GameConfig;

public final class CombatSettings {
    public static final int DEFAULT_BASE_HIT_CHANCE = 75;
    public static final int DEFAULT_BASE_CRIT_CHANCE = 5;
    public static final int DEFAULT_DAMAGE_VARIANCE_PERCENT = 0;
    public static final int DEFAULT_CRIT_MULTIPLIER = 2;
    public static final String DEFAULT_ATTACK_ID = "attack.unarmed";
    public static final int DEFAULT_ATTACK_CADENCE_TICKS = 1;

    private static final GameConfig CONFIG = GameConfig.load();

    private CombatSettings() {
    }

    public static int baseHitChance() {
        return CONFIG.getInt("jmud.combat.base_hit_chance", DEFAULT_BASE_HIT_CHANCE);
    }

    public static int baseCritChance() {
        return CONFIG.getInt("jmud.combat.base_crit_chance", DEFAULT_BASE_CRIT_CHANCE);
    }

    public static int damageVariancePercent() {
        int variance = CONFIG.getInt("jmud.combat.damage_variance_percent", DEFAULT_DAMAGE_VARIANCE_PERCENT);
        if (variance < 0) {
            throw new IllegalArgumentException("Damage variance percent must be non-negative");
        }
        return variance;
    }

    public static int critMultiplier() {
        int multiplier = CONFIG.getInt("jmud.combat.crit_multiplier", DEFAULT_CRIT_MULTIPLIER);
        if (multiplier < 1) {
            throw new IllegalArgumentException("Crit multiplier must be >= 1");
        }
        return multiplier;
    }

    public static AttackId defaultAttackId() {
        return AttackId.of(CONFIG.getString("jmud.combat.default_attack_id", DEFAULT_ATTACK_ID));
    }

    public static int attackCadenceTicks() {
        int cadence = CONFIG.getInt("jmud.combat.attack_cadence_ticks", DEFAULT_ATTACK_CADENCE_TICKS);
        if (cadence < 1) {
            throw new IllegalArgumentException("Attack cadence ticks must be >= 1");
        }
        return cadence;
    }
}
