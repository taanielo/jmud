package io.taanielo.jmud.core.audit;

import io.taanielo.jmud.core.config.GameConfig;

public final class AuditSettings {
    public static final boolean DEFAULT_ENABLED = true;
    public static final String DEFAULT_PATH = "logs/audit.jsonl";
    public static final int DEFAULT_QUEUE_SIZE = 2048;

    private static final GameConfig CONFIG = GameConfig.load();

    private AuditSettings() {
    }

    public static boolean enabled() {
        return CONFIG.getBoolean("jmud.audit.enabled", DEFAULT_ENABLED);
    }

    public static String path() {
        return CONFIG.getString("jmud.audit.path", DEFAULT_PATH);
    }

    public static int queueSize() {
        int size = CONFIG.getInt("jmud.audit.queue_size", DEFAULT_QUEUE_SIZE);
        if (size < 1) {
            throw new IllegalArgumentException("Audit queue size must be >= 1");
        }
        return size;
    }
}
