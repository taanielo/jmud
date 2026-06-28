package io.taanielo.jmud.core.combat.dto;

public final class AttackSchemaVersions {
    public static final int V1 = 1;
    public static final int V2 = 2;
    /** V3 adds the {@code weapon_type} field. */
    public static final int V3 = 3;

    private AttackSchemaVersions() {
    }
}
