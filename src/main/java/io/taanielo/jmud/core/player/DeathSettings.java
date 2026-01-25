package io.taanielo.jmud.core.player;

import io.taanielo.jmud.core.config.GameConfig;

public final class DeathSettings {
    public static final int DEFAULT_RESPAWN_TICKS = 10;

    private static final GameConfig CONFIG = GameConfig.load();

    private DeathSettings() {
    }

    public static int respawnTicks() {
        int ticks = CONFIG.getInt("jmud.death.respawn_ticks", DEFAULT_RESPAWN_TICKS);
        if (ticks < 0) {
            throw new IllegalArgumentException("Respawn ticks must be non-negative");
        }
        return ticks;
    }
}
