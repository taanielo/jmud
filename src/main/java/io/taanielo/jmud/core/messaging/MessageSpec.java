package io.taanielo.jmud.core.messaging;

import java.util.Objects;

public record MessageSpec(MessagePhase phase, MessageChannel channel, String text) {
    public MessageSpec {
        Objects.requireNonNull(phase, "Message phase is required");
        Objects.requireNonNull(channel, "Message channel is required");
        String normalized = normalize(text);
        if (normalized == null) {
            throw new IllegalArgumentException("Message text is required");
        }
        text = normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
