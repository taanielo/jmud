package io.taanielo.jmud.core.combat;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.messaging.MessageSpec;

/**
 * Immutable definition of a single weapon attack, including damage range, bonus
 * statistics, the flavour messages shown to combatants, and the {@link WeaponType}
 * that classifies the delivery style.
 */
public class AttackDefinition {
    private final AttackId id;
    private final String name;
    private final int minDamage;
    private final int maxDamage;
    private final int hitBonus;
    private final int critBonus;
    private final int damageBonus;
    private final List<MessageSpec> messages;
    private final WeaponType weaponType;

    /**
     * Constructs an attack definition with explicit weapon type.
     *
     * @param id          unique identifier for this attack
     * @param name        display name of the attack
     * @param minDamage   minimum damage dealt on a hit (non-negative)
     * @param maxDamage   maximum damage dealt on a hit (must be &gt;= minDamage)
     * @param hitBonus    additive bonus to hit chance
     * @param critBonus   additive bonus to critical hit chance
     * @param damageBonus additive bonus applied to raw damage roll
     * @param messages    flavour messages for hit, miss, and crit phases
     * @param weaponType  classification of the weapon's damage style
     */
    public AttackDefinition(
        AttackId id,
        String name,
        int minDamage,
        int maxDamage,
        int hitBonus,
        int critBonus,
        int damageBonus,
        List<MessageSpec> messages,
        WeaponType weaponType
    ) {
        this.id = Objects.requireNonNull(id, "Attack id is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Attack name must not be blank");
        }
        if (minDamage < 0 || maxDamage < 0) {
            throw new IllegalArgumentException("Attack damage must be non-negative");
        }
        if (maxDamage < minDamage) {
            throw new IllegalArgumentException("Attack max damage must be >= min damage");
        }
        this.name = name;
        this.minDamage = minDamage;
        this.maxDamage = maxDamage;
        this.hitBonus = hitBonus;
        this.critBonus = critBonus;
        this.damageBonus = damageBonus;
        this.messages = List.copyOf(Objects.requireNonNullElse(messages, List.of()));
        this.weaponType = Objects.requireNonNullElse(weaponType, WeaponType.SLASHING);
    }

    /**
     * Constructs an attack definition defaulting to {@link WeaponType#SLASHING}.
     *
     * @param id          unique identifier for this attack
     * @param name        display name of the attack
     * @param minDamage   minimum damage dealt on a hit (non-negative)
     * @param maxDamage   maximum damage dealt on a hit (must be &gt;= minDamage)
     * @param hitBonus    additive bonus to hit chance
     * @param critBonus   additive bonus to critical hit chance
     * @param damageBonus additive bonus applied to raw damage roll
     * @param messages    flavour messages for hit, miss, and crit phases
     */
    public AttackDefinition(
        AttackId id,
        String name,
        int minDamage,
        int maxDamage,
        int hitBonus,
        int critBonus,
        int damageBonus,
        List<MessageSpec> messages
    ) {
        this(id, name, minDamage, maxDamage, hitBonus, critBonus, damageBonus, messages, WeaponType.SLASHING);
    }

    /** @return unique identifier for this attack */
    public AttackId id() {
        return id;
    }

    /** @return display name of the attack */
    public String name() {
        return name;
    }

    /** @return minimum damage dealt on a successful hit */
    public int minDamage() {
        return minDamage;
    }

    /** @return maximum damage dealt on a successful hit */
    public int maxDamage() {
        return maxDamage;
    }

    /** @return additive bonus to hit chance */
    public int hitBonus() {
        return hitBonus;
    }

    /** @return additive bonus to critical hit chance */
    public int critBonus() {
        return critBonus;
    }

    /** @return additive bonus applied to raw damage roll */
    public int damageBonus() {
        return damageBonus;
    }

    /** @return flavour messages for hit, miss, and crit phases */
    public List<MessageSpec> messages() {
        return messages;
    }

    /** @return weapon type classifying this attack's damage style */
    public WeaponType weaponType() {
        return weaponType;
    }
}
