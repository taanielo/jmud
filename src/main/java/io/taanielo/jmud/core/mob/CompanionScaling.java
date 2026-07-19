package io.taanielo.jmud.core.mob;

/**
 * Deterministic level-scaling for player companions (tamed pets via TAME and summoned pets via
 * SUMMON).
 *
 * <p>A companion is spawned from a level-agnostic {@link MobTemplate}, so without scaling a pet
 * tamed or summoned by a level-60 owner would be exactly as strong as one obtained at level 1.
 * This value object turns the owner's <em>current</em> level (at the moment the companion enters
 * the world) into an HP multiplier and a damage multiplier, so a high-level owner's companion both
 * survives longer and hits harder than the identical template obtained by a low-level owner.
 *
 * <p>The multipliers grow linearly with owner level above {@link #BASE_LEVEL} and are hard-capped
 * ({@link #MAX_HP_MULTIPLIER} / {@link #MAX_DAMAGE_MULTIPLIER}) so there are no runaway outlier
 * numbers at very high level. The mapping is a pure function of the owner level: the same owner
 * level and template always yield the same effective stats, so it is fully unit-testable
 * independent of the tick loop and world wiring.
 *
 * @param hpMultiplier     factor applied to a template's max HP (always {@code >= 1.0})
 * @param damageMultiplier factor applied to a companion's resolved hit damage (always {@code >= 1.0})
 */
public record CompanionScaling(double hpMultiplier, double damageMultiplier) {

    /** Owner level at which a companion is unscaled (multipliers are exactly {@code 1.0}). */
    static final int BASE_LEVEL = 1;

    /** Additional max-HP fraction granted per owner level above {@link #BASE_LEVEL}. */
    static final double HP_PER_LEVEL = 0.05;

    /** Additional damage fraction granted per owner level above {@link #BASE_LEVEL}. */
    static final double DAMAGE_PER_LEVEL = 0.04;

    /** Hard cap on the HP multiplier so late-game companions never balloon without bound. */
    static final double MAX_HP_MULTIPLIER = 4.0;

    /** Hard cap on the damage multiplier so late-game companions never balloon without bound. */
    static final double MAX_DAMAGE_MULTIPLIER = 3.0;

    /**
     * Derives the scaling multipliers for a companion whose owner is at {@code ownerLevel}.
     *
     * @param ownerLevel the owner's current level; values below {@link #BASE_LEVEL} are clamped up
     * @return the deterministic, capped scaling for that owner level
     */
    public static CompanionScaling forOwnerLevel(int ownerLevel) {
        int steps = Math.max(BASE_LEVEL, ownerLevel) - BASE_LEVEL;
        double hp = Math.min(MAX_HP_MULTIPLIER, 1.0 + HP_PER_LEVEL * steps);
        double damage = Math.min(MAX_DAMAGE_MULTIPLIER, 1.0 + DAMAGE_PER_LEVEL * steps);
        return new CompanionScaling(hp, damage);
    }

    /**
     * Scales a template's raw max HP by this scaling's HP multiplier.
     *
     * @param templateMaxHp the template's unscaled max HP
     * @return the effective max HP for the companion (at least {@code 1})
     */
    public int scaleMaxHp(int templateMaxHp) {
        return Math.max(1, (int) Math.round(templateMaxHp * hpMultiplier));
    }

    /**
     * Scales a companion's resolved hit damage by this scaling's damage multiplier.
     *
     * @param rawDamage the unscaled resolved damage of a single hit
     * @return the effective damage to apply ({@code rawDamage} unchanged when it is not positive)
     */
    public int scaleDamage(int rawDamage) {
        if (rawDamage <= 0) {
            return rawDamage;
        }
        return Math.max(1, (int) Math.round(rawDamage * damageMultiplier));
    }
}
