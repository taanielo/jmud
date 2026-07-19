package io.taanielo.jmud.core.world;

import java.util.Objects;

import io.taanielo.jmud.core.combat.DamageType;

/**
 * Immutable description of a standing environmental hazard attached to a {@link Room}.
 *
 * <p>A hazardous room periodically deals typed ({@link DamageType#FIRE}, {@link DamageType#COLD},
 * or {@link DamageType#POISON}) damage to every player physically present in it. The damage is a
 * random roll in {@code [damageMin, damageMax]} and is mitigated by the player's equipped
 * elemental-resistance gear exactly as a mob's matching typed attack would be — a fire-resist cloak
 * that shrugs off a fire mob's breath also shrugs off a lava passage's searing fumes.
 *
 * <p>The hazard is never a surprise: {@link RoomRenderer} always surfaces {@link #warningLine()} in
 * the rendered room description so a player can retreat, gear up, or press on with full information.
 * The hazard must be a resistible element (never {@link DamageType#PHYSICAL}) so the promised
 * resistance-gear mitigation always applies.
 *
 * @param damageType    the resistible element this hazard deals; never {@link DamageType#PHYSICAL}
 * @param damageMin     the inclusive minimum raw damage per tick; must be {@code >= 1}
 * @param damageMax     the inclusive maximum raw damage per tick; must be {@code >= damageMin}
 * @param damageMessage the player-facing line delivered to a victim each time the hazard bites
 */
public record RoomHazard(DamageType damageType, int damageMin, int damageMax, String damageMessage) {

    /**
     * Validates the hazard's fields.
     *
     * @throws IllegalArgumentException if the damage type is non-resistible, the damage range is
     *                                  invalid, or the damage message is blank
     */
    public RoomHazard {
        Objects.requireNonNull(damageType, "Hazard damage type is required");
        if (!damageType.isResistible()) {
            throw new IllegalArgumentException("Hazard damage type must be a resistible element, not " + damageType);
        }
        if (damageMin < 1) {
            throw new IllegalArgumentException("Hazard damage_min must be >= 1");
        }
        if (damageMax < damageMin) {
            throw new IllegalArgumentException("Hazard damage_max must be >= damage_min");
        }
        if (damageMessage == null || damageMessage.isBlank()) {
            throw new IllegalArgumentException("Hazard damage message must not be blank");
        }
    }

    /**
     * Returns the always-visible warning line surfaced in the room's rendered description so the
     * danger is stated plainly before a player can be hurt by it.
     *
     * @return a plain-text hazard warning naming the element and the fact that resistance mitigates it
     */
    public String warningLine() {
        return "(Hazard) A lethal " + damageType.displayName()
            + " hazard fills this room; matching resistance gear reduces the damage.";
    }
}
