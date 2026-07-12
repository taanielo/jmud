package io.taanielo.jmud.core.combat.flavor;

import java.util.Objects;

/**
 * The loaded combat-flavor word tables: the damage-verb table and the target-condition table.
 *
 * <p>Both are read once from versioned JSON under {@code data/combat/} and shared, immutably, across
 * the world.
 *
 * @param damageVerbs the damage-verb table
 * @param conditions  the target-condition table
 */
public record CombatFlavor(DamageVerbTable damageVerbs, TargetConditionTable conditions) {
    public CombatFlavor {
        Objects.requireNonNull(damageVerbs, "Damage verb table is required");
        Objects.requireNonNull(conditions, "Condition table is required");
    }
}
