package io.taanielo.jmud.core.combat;

import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.world.EquipmentSlot;
import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.ItemId;

/**
 * Resolves the dual-wield off-hand attack contributed by an attacker's {@link EquipmentSlot#OFFHAND}
 * item.
 *
 * <p>An off-hand item is either a shield (carries a {@code "block_chance"} stat, resolved by
 * {@link ShieldBlockResolver}) or a weapon (carries an {@link Item#getAttackRef() attackRef}) — the
 * two mechanics are mutually exclusive. When the attacker wields a weapon in the off-hand slot this
 * resolver returns that weapon's attack, enabling a second, weaker attack each combat round
 * (see {@link CombatSettings#offhandHitPenaltyPercent()} and
 * {@link CombatSettings#offhandDamagePercent()}). A shield, an empty slot, a broken weapon, or any
 * non-weapon trinket yields {@link Optional#empty()}, leaving combat identical to a single-attack
 * round.
 *
 * <p>The off-hand item is read from the attacker's own inventory (equipped items remain in the
 * inventory), mirroring how {@code resolveAttackId} resolves the main-hand weapon, so this resolver
 * needs no repository and is pure with respect to game state.
 */
public class OffhandAttackResolver {

    private static final String BLOCK_CHANCE_STAT = "block_chance";

    private final boolean enabled;

    /**
     * Creates a resolver that inspects the attacker's off-hand slot for a dual-wield weapon.
     */
    public OffhandAttackResolver() {
        this(true);
    }

    private OffhandAttackResolver(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns a resolver that never grants an off-hand attack, for combat engines wired without
     * dual-wield support (e.g. legacy/test constructors). Combat resolution then rolls a single,
     * main-hand-only attack exactly as before.
     *
     * @return a resolver whose {@link #resolve(Player)} always returns {@link Optional#empty()}
     */
    public static OffhandAttackResolver disabled() {
        return new OffhandAttackResolver(false);
    }

    /**
     * Resolves the off-hand weapon the attacker is dual-wielding, if any.
     *
     * <p>Returns {@link Optional#empty()} when the off-hand slot is empty, holds a shield
     * (a positive {@code block_chance} stat), holds a broken weapon, or holds a non-weapon item with
     * no {@link Item#getAttackRef() attackRef}. Otherwise returns the off-hand weapon's own attack
     * plus its display name for combat messaging.
     *
     * @param attacker the attacking player whose off-hand slot is inspected
     * @return the dual-wield off-hand weapon, or empty when no off-hand attack applies
     */
    public Optional<OffhandWeapon> resolve(Player attacker) {
        Objects.requireNonNull(attacker, "Attacker is required");
        if (!enabled) {
            return Optional.empty();
        }
        ItemId offhandId = attacker.getEquipment().equipped(EquipmentSlot.OFFHAND);
        if (offhandId == null) {
            return Optional.empty();
        }
        for (Item item : attacker.getInventory()) {
            if (!item.getId().equals(offhandId)) {
                continue;
            }
            // A broken weapon cannot be used in combat; a shield (block_chance) is not a weapon.
            if (item.getAttackRef() == null || item.isBroken()) {
                return Optional.empty();
            }
            Integer blockChance = item.getAttributes().getStats().get(BLOCK_CHANCE_STAT);
            if (blockChance != null && blockChance > 0) {
                return Optional.empty();
            }
            return Optional.of(new OffhandWeapon(item.getAttackRef(), item.getName()));
        }
        return Optional.empty();
    }

    /**
     * A dual-wield off-hand weapon: the attack it performs and its display name for messaging.
     *
     * @param attackId   the off-hand weapon's own attack definition id
     * @param weaponName the off-hand weapon's display name (e.g. "Parrying Dagger")
     */
    public record OffhandWeapon(AttackId attackId, String weaponName) {

        /**
         * Creates an off-hand weapon descriptor.
         *
         * @param attackId   the off-hand weapon's own attack definition id; must not be {@code null}
         * @param weaponName the off-hand weapon's display name; must not be {@code null}
         */
        public OffhandWeapon {
            Objects.requireNonNull(attackId, "Attack id is required");
            Objects.requireNonNull(weaponName, "Weapon name is required");
        }
    }
}
