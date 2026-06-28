package io.taanielo.jmud.core.world;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public enum Direction {
    NORTH("north", "n"),
    SOUTH("south", "s"),
    EAST("east", "e"),
    WEST("west", "w"),
    UP("up", "u"),
    DOWN("down", "d");

    private static final Map<String, Direction> LOOKUP = Map.ofEntries(
        Map.entry("north", NORTH),
        Map.entry("n", NORTH),
        Map.entry("south", SOUTH),
        Map.entry("s", SOUTH),
        Map.entry("east", EAST),
        Map.entry("e", EAST),
        Map.entry("west", WEST),
        Map.entry("w", WEST),
        Map.entry("up", UP),
        Map.entry("u", UP),
        Map.entry("down", DOWN),
        Map.entry("d", DOWN)
    );

    private final String label;
    private final String shortLabel;

    Direction(String label, String shortLabel) {
        this.label = label;
        this.shortLabel = shortLabel;
    }

    public String label() {
        return label;
    }

    public String shortLabel() {
        return shortLabel;
    }

    /**
     * Returns the cardinal opposite of this direction
     * (e.g. {@code NORTH.opposite()} returns {@code SOUTH}).
     */
    public Direction opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST  -> WEST;
            case WEST  -> EAST;
            case UP    -> DOWN;
            case DOWN  -> UP;
        };
    }

    public static Optional<Direction> fromInput(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String key = input.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(LOOKUP.get(key));
    }
}
