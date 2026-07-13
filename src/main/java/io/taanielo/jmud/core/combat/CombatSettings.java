package io.taanielo.jmud.core.combat;

import io.taanielo.jmud.core.config.GameConfig;

public final class CombatSettings {
    public static final int DEFAULT_BASE_HIT_CHANCE = 75;
    public static final int DEFAULT_BASE_CRIT_CHANCE = 5;
    public static final int DEFAULT_DAMAGE_VARIANCE_PERCENT = 0;
    public static final int DEFAULT_CRIT_MULTIPLIER = 2;
    public static final int DEFAULT_BLOCK_REDUCTION_PERCENT = 50;
    public static final int DEFAULT_OFFHAND_HIT_PENALTY_PERCENT = 25;
    public static final int DEFAULT_OFFHAND_DAMAGE_PERCENT = 50;
    public static final int DEFAULT_MAX_RESISTANCE_PERCENT = 75;
    public static final String DEFAULT_ATTACK_ID = "attack.unarmed";
    public static final int DEFAULT_ATTACK_CADENCE_TICKS = 1;

    /**
     * Lower bound applied to a resolved hit chance, so even a heavily out-matched attacker retains a
     * slim chance to land a blow.
     */
    public static final int MIN_HIT_CHANCE = 5;

    /**
     * Upper bound applied to a resolved hit chance, so even a dominant attacker can still miss and no
     * fight is ever a certainty.
     */
    public static final int MAX_HIT_CHANCE = 95;

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

    /**
     * The default percentage by which a successful shield block reduces incoming damage,
     * used when an off-hand shield item declares a {@code block_chance} stat but no explicit
     * {@code block_reduction} stat. Clamped to the range {@code [0, 100]}.
     *
     * @return the default block-reduction percentage in {@code [0, 100]}
     */
    public static int defaultBlockReductionPercent() {
        int reduction = CONFIG.getInt(
            "jmud.combat.default_block_reduction_percent", DEFAULT_BLOCK_REDUCTION_PERCENT);
        if (reduction < 0 || reduction > 100) {
            throw new IllegalArgumentException("Default block reduction percent must be in [0, 100]");
        }
        return reduction;
    }

    /**
     * The hit-chance percentage subtracted from a dual-wield off-hand attack, making the second
     * (off-hand) swing land less reliably than the main-hand attack. Clamped to {@code [0, 100]};
     * a value of {@code 0} means the off-hand attack is as accurate as the main hand.
     *
     * @return the off-hand hit-chance penalty in percentage points, in {@code [0, 100]}
     */
    public static int offhandHitPenaltyPercent() {
        int penalty = CONFIG.getInt(
            "jmud.combat.offhand_hit_penalty_percent", DEFAULT_OFFHAND_HIT_PENALTY_PERCENT);
        if (penalty < 0 || penalty > 100) {
            throw new IllegalArgumentException("Offhand hit penalty percent must be in [0, 100]");
        }
        return penalty;
    }

    /**
     * The percentage of a normal attack's damage that a dual-wield off-hand swing deals, making the
     * second (off-hand) hit weaker than the main-hand attack. Clamped to {@code [0, 100]}; a value of
     * {@code 100} means the off-hand hits for full damage while {@code 50} halves it.
     *
     * @return the off-hand damage multiplier as a percentage, in {@code [0, 100]}
     */
    public static int offhandDamagePercent() {
        int percent = CONFIG.getInt(
            "jmud.combat.offhand_damage_percent", DEFAULT_OFFHAND_DAMAGE_PERCENT);
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Offhand damage percent must be in [0, 100]");
        }
        return percent;
    }

    /**
     * The maximum percentage by which equipped elemental resistance may reduce incoming
     * non-physical damage. Capped so that stacking resistance gear can never grant full immunity —
     * a resisted blow always deals at least {@code 100 - maxResistancePercent()} percent of its
     * computed damage (before the 1-damage floor). Clamped to the range {@code [0, 100]}.
     *
     * @return the resistance mitigation cap as a percentage, in {@code [0, 100]}
     */
    public static int maxResistancePercent() {
        int cap = CONFIG.getInt("jmud.combat.max_resistance_percent", DEFAULT_MAX_RESISTANCE_PERCENT);
        if (cap < 0 || cap > 100) {
            throw new IllegalArgumentException("Max resistance percent must be in [0, 100]");
        }
        return cap;
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
