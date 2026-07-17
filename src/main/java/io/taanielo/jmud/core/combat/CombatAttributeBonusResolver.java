package io.taanielo.jmud.core.combat;

import java.util.Objects;

import io.taanielo.jmud.core.character.CharacterAttributes;
import io.taanielo.jmud.core.character.CharacterAttributesResolver;
import io.taanielo.jmud.core.player.Player;

/**
 * Translates a combatant's derived {@link CharacterAttributes} into the additive combat terms that
 * feed the hit/damage/crit pipeline: strength-based physical damage, agility-based accuracy, dodge
 * and critical chance. This is the single combat-facing adapter over
 * {@link CharacterAttributesResolver}, keeping the attribute formulas in one place.
 *
 * <p>All terms are zero for a combatant with all-baseline attributes, so wiring this resolver in
 * leaves existing all-baseline fights numerically unchanged.
 */
public class CombatAttributeBonusResolver {

    private final CharacterAttributesResolver attributesResolver;

    /**
     * Creates a resolver backed by the given character-attributes resolver.
     *
     * @param attributesResolver resolves a combatant's derived attributes from race, class and level
     */
    public CombatAttributeBonusResolver(CharacterAttributesResolver attributesResolver) {
        this.attributesResolver =
            Objects.requireNonNull(attributesResolver, "Character attributes resolver is required");
    }

    /**
     * Returns a resolver that contributes zero to every combat term, for legacy constructors and
     * test contexts where attribute data is unavailable.
     *
     * @return a no-op resolver that leaves combat results unchanged
     */
    public static CombatAttributeBonusResolver noOp() {
        return new CombatAttributeBonusResolver(CharacterAttributesResolver.baselineOnly());
    }

    private CharacterAttributes attributesOf(Player combatant) {
        return attributesResolver.resolve(combatant.getRace(), combatant.getClassId(), combatant.getLevel());
    }

    /**
     * Returns the flat physical damage added to an attacker's weapon roll from strength,
     * {@code floor((STR - 10) / 2)}. May be negative for a below-baseline attacker.
     *
     * @param attacker the attacking combatant
     * @return the strength damage bonus (signed)
     */
    public int meleeDamageBonus(Player attacker) {
        Objects.requireNonNull(attacker, "Attacker is required");
        return Math.floorDiv(attributesOf(attacker).strengthModifier(), 2);
    }

    /**
     * Returns the percentage-point hit-chance bonus an attacker gains from agility,
     * {@code (AGI - 10)}. May be negative.
     *
     * @param attacker the attacking combatant
     * @return the agility hit-chance bonus (signed)
     */
    public int hitChanceBonus(Player attacker) {
        Objects.requireNonNull(attacker, "Attacker is required");
        return attributesOf(attacker).agilityModifier();
    }

    /**
     * Returns the percentage-point hit-chance reduction a defender imposes through agility (dodge),
     * {@code floor((AGI - 10) / 2)}. May be negative for a below-baseline defender.
     *
     * @param defender the defending combatant
     * @return the agility dodge value (signed)
     */
    public int dodgeBonus(Player defender) {
        Objects.requireNonNull(defender, "Defender is required");
        return Math.floorDiv(attributesOf(defender).agilityModifier(), 2);
    }

    /**
     * Returns the percentage-point critical-chance bonus an attacker gains from agility,
     * {@code floor((AGI - 10) / 2)}. May be negative.
     *
     * @param attacker the attacking combatant
     * @return the agility crit-chance bonus (signed)
     */
    public int critChanceBonus(Player attacker) {
        Objects.requireNonNull(attacker, "Attacker is required");
        return Math.floorDiv(attributesOf(attacker).agilityModifier(), 2);
    }

    /**
     * Returns the raw percentage-point parry chance a defender derives from agility,
     * {@code (AGI - 10)}, before it is clamped to
     * {@code [CombatSettings.MIN_PARRY_CHANCE, CombatSettings.MAX_PARRY_CHANCE]} by
     * {@link ParryResolver}. A baseline-agility defender yields {@code 0} (never parries); a
     * below-baseline defender yields a negative value that clamps back to the floor.
     *
     * @param defender the defending combatant
     * @return the agility parry-chance term (signed, unclamped)
     */
    public int parryChanceBonus(Player defender) {
        Objects.requireNonNull(defender, "Defender is required");
        return attributesOf(defender).agilityModifier();
    }
}
