package io.taanielo.jmud.core.combat;

/**
 * Classifies whether an {@link AttackDefinition} can only be used in melee (same room) or can
 * also strike a target in an adjacent room.
 *
 * <p>Melee is the default for existing weapons; ranged weapons (bows, throwing knives) declare
 * {@link #RANGED} so the {@code SHOOT} command may fire them across a room boundary.
 */
public enum RangeType {

    /** A close-combat attack that can only reach a target in the same room. */
    MELEE,

    /** A ranged attack that can strike a target in an adjacent room via the {@code SHOOT} command. */
    RANGED
}
