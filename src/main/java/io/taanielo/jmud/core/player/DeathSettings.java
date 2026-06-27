package io.taanielo.jmud.core.player;

import io.taanielo.jmud.core.config.GameConfig;

public final class DeathSettings {
    public static final int DEFAULT_RESPAWN_TICKS = 10;
    public static final int DEFAULT_CORPSE_DECAY_SECONDS = 300;
    /** Room id where players respawn after death. */
    public static final String RESPAWN_ROOM_ID = "training-yard";

    private static final GameConfig CONFIG = GameConfig.load();

    private DeathSettings() {
    }

    /**
     * Returns the number of ticks to wait before the dead player automatically respawns.
     */
    public static int respawnTicks() {
        int ticks = CONFIG.getInt("jmud.death.respawn_ticks", DEFAULT_RESPAWN_TICKS);
        if (ticks < 0) {
            throw new IllegalArgumentException("Respawn ticks must be non-negative");
        }
        return ticks;
    }

    /**
     * Returns how many seconds after spawning a player corpse decays and disappears.
     */
    public static int corpseDecaySeconds() {
        int seconds = CONFIG.getInt("jmud.death.corpse_decay_seconds", DEFAULT_CORPSE_DECAY_SECONDS);
        if (seconds <= 0) {
            throw new IllegalArgumentException("Corpse decay seconds must be positive");
        }
        return seconds;
    }
}
