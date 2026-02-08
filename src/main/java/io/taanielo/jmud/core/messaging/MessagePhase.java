package io.taanielo.jmud.core.messaging;

import java.util.Locale;

public enum MessagePhase {
    APPLY,
    TICK,
    EXPIRE,
    EXAMINE,
    USE,
    QUAFF,
    PICKUP,
    DROP,
    ATTACK_HIT,
    ATTACK_MISS,
    ATTACK_CRIT;

    public static MessagePhase fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Message phase is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Message phase is required");
        }
        return MessagePhase.valueOf(normalized);
    }
}
