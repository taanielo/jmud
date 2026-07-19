package io.taanielo.jmud.core.combat;

import java.util.Objects;

import io.taanielo.jmud.core.combat.repository.AttackRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Resolves a defender's parry profile: the chance, derived from agility, to fully avoid an incoming
 * melee hit and answer it with a free riposte.
 *
 * <p>Parry is the weapon-driven counterpart to {@link ShieldBlockResolver}: where block requires an
 * off-hand shield, parry only requires the defender to be wielding a melee weapon in their
 * {@link EquipmentSlot#WEAPON} slot. An unarmed defender, or one wielding a ranged weapon
 * ({@link RangeType#RANGED}), never parries — so a two-hander, dual-wielder, or shieldless caster
 * still gains an active defensive layer that scales with their agility rather than their gear.
 *
 * <p>The parry chance is {@code (AGI - 10)} (see
 * {@link CombatAttributeBonusResolver#parryChanceBonus(Player)}) clamped to
 * {@code [CombatSettings.MIN_PARRY_CHANCE, CombatSettings.MAX_PARRY_CHANCE]}. A baseline-agility
 * defender resolves to {@code 0} and therefore never parries, leaving all-baseline fights — and the
 * combat RNG stream — numerically unchanged.
 *
 * <p>The mainhand weapon is read from the defender's own inventory (equipped items remain in the
 * inventory), mirroring {@link OffhandAttackResolver}. Its {@link AttackDefinition} is loaded from the
 * {@link AttackRepository} both to confirm the weapon is melee and to supply the riposte's damage roll.
 */
public class ParryResolver {

    private final AttackRepository attackRepository;
    private final CombatAttributeBonusResolver attributeBonusResolver;

    /**
     * Creates a resolver backed by the given attack repository and attribute-bonus resolver.
     *
     * @param attackRepository       source of the mainhand weapon's attack definition (melee gate +
     *                               riposte damage roll)
     * @param attributeBonusResolver resolves the defender's agility-derived parry chance
     */
    public ParryResolver(AttackRepository attackRepository, CombatAttributeBonusResolver attributeBonusResolver) {
        this.attackRepository = Objects.requireNonNull(attackRepository, "Attack repository is required");
        this.attributeBonusResolver =
            Objects.requireNonNull(attributeBonusResolver, "Attribute bonus resolver is required");
    }

    /**
     * Returns a no-op resolver that never grants a parry chance, for combat engines wired without
     * parry support (e.g. legacy/test constructors).
     *
     * @return a resolver whose {@link #resolve(Player)} always returns {@link Parry#none()}
     */
    public static ParryResolver noOp() {
        return new ParryResolver(id -> java.util.Optional.empty(), CombatAttributeBonusResolver.noOp());
    }

    /**
     * Resolves the parry profile of the defender's currently equipped mainhand weapon.
     *
     * <p>Returns {@link Parry#none()} when the defender is unarmed, the mainhand item is broken or
     * carries no attack, the weapon is ranged, its attack definition cannot be loaded, or the
     * resolved (clamped) parry chance is not positive. Otherwise returns the clamped chance together
     * with the weapon's {@link AttackDefinition}, which supplies the riposte damage roll.
     *
     * @param defender the defending player whose mainhand slot is inspected
     * @return the resolved parry profile; never {@code null}
     */
    public Parry resolve(Player defender) {
        Objects.requireNonNull(defender, "Defender is required");
        ItemId weaponId = defender.getEquipment().equipped(EquipmentSlot.WEAPON);
        if (weaponId == null) {
            return Parry.none();
        }
        for (Item item : defender.getInventory()) {
            if (!item.getId().equals(weaponId)) {
                continue;
            }
            // A broken weapon or a non-weapon item confers no parry; an unarmed strike cannot parry.
            if (item.getAttackRef() == null || item.isBroken()) {
                return Parry.none();
            }
            try {
                AttackDefinition attack = attackRepository.findById(item.getAttackRef()).orElse(null);
                // Only a melee weapon parries; a ranged weapon (bow) in the mainhand never does.
                if (attack == null || attack.rangeType() != RangeType.MELEE) {
                    return Parry.none();
                }
                int chance = clamp(
                    attributeBonusResolver.parryChanceBonus(defender),
                    CombatSettings.MIN_PARRY_CHANCE, CombatSettings.MAX_PARRY_CHANCE);
                if (chance <= 0) {
                    return Parry.none();
                }
                return new Parry(chance, attack);
            } catch (RepositoryException e) {
                // A missing weapon attack should never abort combat; treat as no parry.
                return Parry.none();
            }
        }
        return Parry.none();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * A defender's parry profile: the percentage chance to fully avoid an otherwise-landing melee hit
     * and the mainhand {@link AttackDefinition} whose damage roll powers the riposte.
     *
     * @param chancePercent  chance to parry, in {@code [0, 100]}; {@code 0} means no parry is possible
     * @param riposteAttack  the mainhand weapon's attack definition used for the riposte damage roll;
     *                       {@code null} when no parry is possible
     */
    public record Parry(int chancePercent, AttackDefinition riposteAttack) {

        private static final Parry NONE = new Parry(0, null);

        /**
         * Returns the empty profile granting no parry chance.
         *
         * @return a profile whose {@link #chancePercent()} is {@code 0}
         */
        public static Parry none() {
            return NONE;
        }

        /**
         * Whether this profile can produce a parry.
         *
         * @return {@code true} when {@link #chancePercent()} is positive and a riposte attack is present
         */
        public boolean canParry() {
            return chancePercent > 0 && riposteAttack != null;
        }
    }
}
