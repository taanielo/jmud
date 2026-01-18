package io.taanielo.jmud.core.combat;

import java.util.Objects;

public class AttackDefinition {
    private final AttackId id;
    private final String name;
    private final int minDamage;
    private final int maxDamage;
    private final int hitBonus;
    private final int critBonus;
    private final int damageBonus;

    public AttackDefinition(
        AttackId id,
        String name,
        int minDamage,
        int maxDamage,
        int hitBonus,
        int critBonus,
        int damageBonus
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
    }

    public AttackId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int minDamage() {
        return minDamage;
    }

    public int maxDamage() {
        return maxDamage;
    }

    public int hitBonus() {
        return hitBonus;
    }

    public int critBonus() {
        return critBonus;
    }

    public int damageBonus() {
        return damageBonus;
    }
}
