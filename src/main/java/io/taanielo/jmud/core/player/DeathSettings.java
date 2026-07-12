package io.taanielo.jmud.core.player;

import io.taanielo.jmud.core.config.GameConfig;

public final class DeathSettings {
    public static final int DEFAULT_RESPAWN_TICKS = 10;
    public static final int DEFAULT_CORPSE_DECAY_SECONDS = 300;
    /**
     * Default level below which a dying player is spared the corpse-drop mechanic and keeps all
     * carried gold and items on respawn.
     */
    public static final int DEFAULT_GRACE_LEVEL = 5;
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

    /**
     * Returns the character level below which dying does not drop the player's gold and items into a
     * corpse. Newbie characters (level {@code < graceLevel()}) keep all their possessions on respawn,
     * sparing them the corpse-run death spiral. A value of {@code 0} disables the grace entirely.
     */
    public static int graceLevel() {
        int level = CONFIG.getInt("jmud.death.grace_level", DEFAULT_GRACE_LEVEL);
        if (level < 0) {
            throw new IllegalArgumentException("Grace level must be non-negative");
        }
        return level;
    }

    /**
     * Returns whether a player at the given level is protected by newbie death grace, i.e. their
     * gold and items are preserved through death instead of being dropped into a corpse.
     *
     * @param level the dying player's character level
     * @return {@code true} if the player keeps their possessions on death
     */
    public static boolean isGraceProtected(int level) {
        return level < graceLevel();
    }
}
