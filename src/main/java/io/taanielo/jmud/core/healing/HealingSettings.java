package io.taanielo.jmud.core.healing;

import io.taanielo.jmud.core.config.GameConfig;

public final class HealingSettings {
    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_BASE_HP_PER_TICK = 1;

    private static final GameConfig CONFIG = GameConfig.load();

    private HealingSettings() {
    }

    public static boolean enabled() {
        return CONFIG.getBoolean("jmud.healing.enabled", DEFAULT_ENABLED);
    }

    public static int baseHpPerTick() {
        int base = CONFIG.getInt("jmud.healing.base_hp_per_tick", DEFAULT_BASE_HP_PER_TICK);
        if (base < 0) {
            throw new IllegalArgumentException("Base healing per tick must be non-negative");
        }
        return base;
    }

    public static int classModifier(io.taanielo.jmud.core.character.ClassId classId) {
        String key = "jmud.healing.class." + normalize(classId) + ".modifier";
        return CONFIG.getInt(key, 0);
    }

    private static String normalize(Object id) {
        if (id == null) {
            return "unknown";
        }
        String value = switch (id) {
            case io.taanielo.jmud.core.character.ClassId clazz -> clazz.getValue();
            default -> String.valueOf(id);
        };
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase();
    }

}
