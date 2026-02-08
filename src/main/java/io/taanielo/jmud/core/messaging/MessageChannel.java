package io.taanielo.jmud.core.messaging;

import java.util.Locale;

public enum MessageChannel {
    SELF,
    TARGET,
    ROOM;

    public static MessageChannel fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Message channel is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Message channel is required");
        }
        return MessageChannel.valueOf(normalized);
    }
}
