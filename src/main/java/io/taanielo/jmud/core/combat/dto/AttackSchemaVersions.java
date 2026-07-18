package io.taanielo.jmud.core.combat.dto;

public final class AttackSchemaVersions {
    public static final int V1 = 1;
    public static final int V2 = 2;
    /** V3 adds the {@code weapon_type} field. */
    public static final int V3 = 3;
    /** V4 adds the optional {@code applies_effect} field (on-hit status effect application). */
    public static final int V4 = 4;
    /** V5 adds the optional {@code range_type} field (MELEE or RANGED). */
    public static final int V5 = 5;
    /** V6 adds the optional {@code damage_type} field (PHYSICAL, FIRE, COLD, POISON, …). */
    public static final int V6 = 6;
    /** V7 adds the optional {@code telegraph_ticks} field (mob wind-up delay before a special lands). */
    public static final int V7 = 7;

    private AttackSchemaVersions() {
    }
}
