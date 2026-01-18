package io.taanielo.jmud.core.server.socket;

import java.util.Locale;

/**
 * Utility methods for parsing socket command input.
 */
public final class SocketCommandParsing {
    private SocketCommandParsing() {
    }

    static String firstToken(String input) {
        return splitInput(input)[0];
    }

    static String[] splitInput(String input) {
        String trimmed = input == null ? "" : input.trim();
        if (trimmed.isEmpty()) {
            return new String[] {"", ""};
        }
        String[] parts = trimmed.split("\\s+", 2);
        String token = parts[0].toUpperCase(Locale.ROOT);
        String args = parts.length > 1 ? parts[1].trim() : "";
        return new String[] {token, args};
    }
}
