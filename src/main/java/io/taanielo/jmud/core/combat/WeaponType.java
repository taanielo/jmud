package io.taanielo.jmud.core.combat;

/**
 * Classifies a weapon's damage delivery style.
 *
 * <p>Each type carries its own flavour verbs and is expected to have characteristic
 * damage-dice ranges defined in the corresponding {@link AttackDefinition} data files.
 */
public enum WeaponType {

    /** Blunt weapons (maces, hammers) — slower, higher minimum damage. */
    BLUNT,

    /** Piercing weapons (daggers, spears) — faster, lower maximum damage. */
    PIERCING,

    /** Slashing weapons (swords, axes) — balanced damage profile. */
    SLASHING
}
