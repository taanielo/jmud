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
    private final AttackEffectApplication effectOnHit;
    private final RangeType rangeType;
    private final DamageType damageType;
    private final int telegraphTicks;

    /**
     * Constructs an attack definition with explicit weapon type, an optional on-hit status effect
     * application, an explicit {@link RangeType}, an explicit {@link DamageType}, and an explicit
     * telegraph delay.
     *
     * @param id             unique identifier for this attack
     * @param name           display name of the attack
     * @param minDamage      minimum damage dealt on a hit (non-negative)
     * @param maxDamage      maximum damage dealt on a hit (must be &gt;= minDamage)
     * @param hitBonus       additive bonus to hit chance
     * @param critBonus      additive bonus to critical hit chance
     * @param damageBonus    additive bonus applied to raw damage roll
     * @param messages       flavour messages for hit, miss, crit, and telegraph phases
     * @param weaponType     classification of the weapon's damage style
     * @param effectOnHit    optional status effect to apply to the target on a successful hit;
     *                       {@code null} means this attack applies no effect
     * @param rangeType      whether this attack is melee-only or can strike an adjacent room;
     *                       {@code null} defaults to {@link RangeType#MELEE}
     * @param damageType     the elemental nature of the damage; {@code null} defaults to
     *                       {@link DamageType#PHYSICAL}
     * @param telegraphTicks number of AI ticks a mob winds up before this attack lands; {@code 0}
     *                       (the default) means the attack resolves instantly with no telegraph.
     *                       Negative values are clamped to {@code 0}
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
        WeaponType weaponType,
        AttackEffectApplication effectOnHit,
        RangeType rangeType,
        DamageType damageType,
        int telegraphTicks
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
        this.effectOnHit = effectOnHit;
        this.rangeType = Objects.requireNonNullElse(rangeType, RangeType.MELEE);
        this.damageType = Objects.requireNonNullElse(damageType, DamageType.PHYSICAL);
        this.telegraphTicks = Math.max(0, telegraphTicks);
    }

    /**
     * Constructs an attack definition with explicit weapon type, an optional on-hit status effect
     * application, an explicit {@link RangeType}, and an explicit {@link DamageType}, defaulting to
     * no telegraph delay (instant resolution).
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
     * @param effectOnHit optional status effect to apply to the target on a successful hit;
     *                    {@code null} means this attack applies no effect
     * @param rangeType   whether this attack is melee-only or can strike an adjacent room;
     *                    {@code null} defaults to {@link RangeType#MELEE}
     * @param damageType  the elemental nature of the damage; {@code null} defaults to
     *                    {@link DamageType#PHYSICAL}
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
        WeaponType weaponType,
        AttackEffectApplication effectOnHit,
        RangeType rangeType,
        DamageType damageType
    ) {
        this(id, name, minDamage, maxDamage, hitBonus, critBonus, damageBonus, messages,
            weaponType, effectOnHit, rangeType, damageType, 0);
    }

    /**
     * Constructs an attack definition with explicit weapon type, an optional on-hit status
     * effect application, and an explicit {@link RangeType}, defaulting the damage type to
     * {@link DamageType#PHYSICAL}.
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
     * @param effectOnHit optional status effect to apply to the target on a successful hit;
     *                    {@code null} means this attack applies no effect
     * @param rangeType   whether this attack is melee-only or can strike an adjacent room;
     *                    {@code null} defaults to {@link RangeType#MELEE}
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
        WeaponType weaponType,
        AttackEffectApplication effectOnHit,
        RangeType rangeType
    ) {
        this(id, name, minDamage, maxDamage, hitBonus, critBonus, damageBonus, messages,
            weaponType, effectOnHit, rangeType, DamageType.PHYSICAL);
    }

    /**
     * Constructs an attack definition with explicit weapon type and an optional
     * on-hit status effect application, defaulting to {@link RangeType#MELEE}.
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
     * @param effectOnHit optional status effect to apply to the target on a successful hit;
     *                    {@code null} means this attack applies no effect
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
        WeaponType weaponType,
        AttackEffectApplication effectOnHit
    ) {
        this(id, name, minDamage, maxDamage, hitBonus, critBonus, damageBonus, messages,
            weaponType, effectOnHit, RangeType.MELEE);
    }

    /**
     * Constructs an attack definition with explicit weapon type and no on-hit effect.
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
        this(id, name, minDamage, maxDamage, hitBonus, critBonus, damageBonus, messages, weaponType, null);
    }

    /**
     * Constructs an attack definition defaulting to {@link WeaponType#SLASHING} and no on-hit effect.
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
        this(id, name, minDamage, maxDamage, hitBonus, critBonus, damageBonus, messages, WeaponType.SLASHING, null);
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

    /** @return the on-hit status effect application, or {@code null} if this attack applies no effect */
    public AttackEffectApplication effectOnHit() {
        return effectOnHit;
    }

    /** @return whether this attack is melee-only or can strike an adjacent room */
    public RangeType rangeType() {
        return rangeType;
    }

    /** @return the elemental nature of this attack's damage; never {@code null} */
    public DamageType damageType() {
        return damageType;
    }

    /** @return {@code true} when this attack can be fired at a target in an adjacent room */
    public boolean isRanged() {
        return rangeType == RangeType.RANGED;
    }

    /**
     * @return the number of AI ticks a mob winds up before this attack lands; {@code 0} means the
     *         attack resolves instantly with no telegraph (today's default behaviour)
     */
    public int telegraphTicks() {
        return telegraphTicks;
    }

    /** @return {@code true} when this attack telegraphs (winds up over one or more ticks) before landing */
    public boolean telegraphs() {
        return telegraphTicks > 0;
    }
}
