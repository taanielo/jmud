package io.taanielo.jmud.core.player;

import io.taanielo.jmud.core.config.GameConfig;

/**
 * Configuration for the rest-regen system.
 *
 * <p>Values are read from {@code jmud.properties} on first access.
 */
public final class RestSettings {

    public static final int DEFAULT_REGEN_AMOUNT = 2;

    private static final GameConfig CONFIG = GameConfig.load();

    private RestSettings() {
    }

    /** HP restored per rest tick. Defaults to {@value #DEFAULT_REGEN_AMOUNT}. */
    public static int regenHp() {
        return CONFIG.getInt("jmud.rest.regen.hp", DEFAULT_REGEN_AMOUNT);
    }

    /** Mana restored per rest tick. Defaults to {@value #DEFAULT_REGEN_AMOUNT}. */
    public static int regenMana() {
        return CONFIG.getInt("jmud.rest.regen.mana", DEFAULT_REGEN_AMOUNT);
    }

    /** Move restored per rest tick. Defaults to {@value #DEFAULT_REGEN_AMOUNT}. */
    public static int regenMove() {
        return CONFIG.getInt("jmud.rest.regen.move", DEFAULT_REGEN_AMOUNT);
    }
}
