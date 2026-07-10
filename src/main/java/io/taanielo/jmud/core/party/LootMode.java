package io.taanielo.jmud.core.party;

import java.util.Locale;
import java.util.Optional;

/**
 * Determines how a party's mob-kill item drops are assigned.
 *
 * <ul>
 *   <li>{@link #FREE} — items drop to the room floor for anyone to {@code GET} (the default,
 *       matching solo behaviour).</li>
 *   <li>{@link #ROUND_ROBIN} — each dropped item is assigned directly to a party member present in
 *       the room, cycling through eligible members in turn across kills.</li>
 * </ul>
 */
public enum LootMode {

    /** Items drop to the room floor for anyone to pick up (default). */
    FREE("free"),

    /** Items are assigned to party members in rotating order. */
    ROUND_ROBIN("round-robin");

    private final String label;

    LootMode(String label) {
        this.label = label;
    }

    /**
     * Returns the player-facing label of this loot mode (e.g. {@code "round-robin"}).
     *
     * @return the lowercase display label
     */
    public String label() {
        return label;
    }

    /**
     * Parses a player-supplied loot-mode argument, accepting the canonical labels as well as common
     * spelling variants ({@code roundrobin}, {@code round_robin}). Matching is case-insensitive.
     *
     * @param input the raw argument text
     * @return the matching mode, or empty when the input names no known mode
     */
    public static Optional<LootMode> parse(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "free" -> Optional.of(FREE);
            case "round-robin", "roundrobin", "round_robin", "rr" -> Optional.of(ROUND_ROBIN);
            default -> Optional.empty();
        };
    }
}
