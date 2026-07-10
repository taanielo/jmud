package io.taanielo.jmud.core.bank;

import io.taanielo.jmud.core.config.GameConfig;

/**
 * Configuration for the town bank.
 *
 * <p>Values are read from {@code jmud.properties} on first access.
 */
public final class BankSettings {

    /** Default number of item slots in a player's personal bank vault. */
    public static final int DEFAULT_VAULT_CAPACITY = 30;

    private static final GameConfig CONFIG = GameConfig.load();

    private BankSettings() {
    }

    /**
     * Maximum number of items a player may keep in their bank vault.
     * Defaults to {@value #DEFAULT_VAULT_CAPACITY}.
     */
    public static int vaultCapacity() {
        return CONFIG.getInt("jmud.bank.vault.capacity", DEFAULT_VAULT_CAPACITY);
    }
}
