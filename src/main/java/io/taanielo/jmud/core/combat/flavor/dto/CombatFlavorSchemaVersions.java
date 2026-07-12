package io.taanielo.jmud.core.combat.flavor.dto;

/**
 * Supported schema versions for the combat-flavor JSON files.
 *
 * <p>Both {@code data/combat/damage-verbs.json} and {@code data/combat/conditions.json} are at V1.
 * V1 supports two verb conjugations per tier ({@code third_person}, {@code second_person}); the
 * schema is intentionally open to optional per-damage-type verb columns being added later without a
 * version bump for existing files.
 */
public final class CombatFlavorSchemaVersions {
    public static final int V1 = 1;

    private CombatFlavorSchemaVersions() {
    }
}
