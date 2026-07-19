package io.taanielo.jmud.core.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.output.PlainTextStyler;
import io.taanielo.jmud.core.output.TextStyler;

/**
 * Stateless service that composes the look description for a room.
 *
 * <p>Accepts a fully-populated {@link Room} (with occupants and merged items already embedded)
 * plus the set of currently locked exits for the room, and returns the rendered output lines.
 * Contains no mutable state and may be called from any thread.
 */
public class RoomRenderer {

    /**
     * Controls whether a room's prose description line is included in the rendered output.
     *
     * <p>{@link #FULL} renders the complete description (used by explicit {@code LOOK}); {@link #BRIEF}
     * omits the prose line and renders only the room name, exits, items, and occupants (used by
     * movement when the viewer has brief mode enabled).
     */
    public enum DescriptionMode {
        /** Include the room's prose description line. */
        FULL,
        /** Omit the room's prose description line. */
        BRIEF
    }

    private static final TextStyler PLAIN = new PlainTextStyler();

    /**
     * Returns the rendered look description lines for the given room at {@link TimeOfDay#DAY}.
     *
     * @param room        the room to describe (with occupants and merged items already embedded)
     * @param viewer      the player requesting the description (excluded from the occupants list)
     * @param lockedExits the set of currently locked exit directions in this room
     * @return the list of output lines (name, description, exits, items, occupants)
     */
    public List<String> describeRoom(Room room, Username viewer, Set<Direction> lockedExits) {
        return describeRoom(room, viewer, lockedExits, TimeOfDay.DAY, PLAIN);
    }

    /**
     * Returns the rendered look description lines for the given room, selecting the room's
     * alternate night description (see {@link Room#describeFor(TimeOfDay)}) when {@code timeOfDay}
     * is {@link TimeOfDay#NIGHT} and one is defined.
     *
     * @param room        the room to describe (with occupants and merged items already embedded)
     * @param viewer      the player requesting the description (excluded from the occupants list)
     * @param lockedExits the set of currently locked exit directions in this room
     * @param timeOfDay   the current time of day, used to pick the day or night description
     * @return the list of output lines (name, description, exits, items, occupants)
     */
    public List<String> describeRoom(Room room, Username viewer, Set<Direction> lockedExits, TimeOfDay timeOfDay) {
        return describeRoom(room, viewer, lockedExits, timeOfDay, PLAIN);
    }

    /**
     * Returns the rendered look description lines for the given room, coloring each room item name
     * by its rarity tier through the supplied {@link TextStyler}.
     *
     * @param room        the room to describe (with occupants and merged items already embedded)
     * @param viewer      the player requesting the description (excluded from the occupants list)
     * @param lockedExits the set of currently locked exit directions in this room
     * @param timeOfDay   the current time of day, used to pick the day or night description
     * @param styler      the styler used to color item names by rarity
     * @return the list of output lines (name, description, exits, items, occupants)
     */
    public List<String> describeRoom(
        Room room, Username viewer, Set<Direction> lockedExits, TimeOfDay timeOfDay, TextStyler styler) {
        return describeRoom(room, viewer, lockedExits, timeOfDay, styler, DescriptionMode.FULL);
    }

    /**
     * Returns the rendered look description lines for the given room, omitting the prose description
     * line when {@code mode} is {@link DescriptionMode#BRIEF}.
     *
     * @param room        the room to describe (with occupants and merged items already embedded)
     * @param viewer      the player requesting the description (excluded from the occupants list)
     * @param lockedExits the set of currently locked exit directions in this room
     * @param timeOfDay   the current time of day, used to pick the day or night description
     * @param styler      the styler used to color item names by rarity
     * @param mode        whether to include ({@link DescriptionMode#FULL}) or omit
     *                    ({@link DescriptionMode#BRIEF}) the prose description line
     * @return the list of output lines (name, optional description, exits, items, occupants)
     */
    public List<String> describeRoom(
        Room room,
        Username viewer,
        Set<Direction> lockedExits,
        TimeOfDay timeOfDay,
        TextStyler styler,
        DescriptionMode mode) {
        Objects.requireNonNull(room, "Room is required");
        Objects.requireNonNull(viewer, "Viewer is required");
        Objects.requireNonNull(lockedExits, "Locked exits set is required");
        Objects.requireNonNull(timeOfDay, "Time of day is required");
        Objects.requireNonNull(styler, "Styler is required");
        Objects.requireNonNull(mode, "Description mode is required");
        List<String> lines = new ArrayList<>();
        lines.add(room.getName());
        if (mode == DescriptionMode.FULL) {
            lines.add(room.describeFor(timeOfDay));
        }
        // A standing hazard is always surfaced (even in BRIEF movement output) so the danger is never
        // a surprise: a player can always retreat, gear up, or press on with full information.
        if (room.hasHazard()) {
            lines.add(room.getHazard().warningLine());
        }
        lines.add("Exits: " + formatExits(room.getExits(), lockedExits));
        lines.add("Items: " + formatItems(room.getItems(), styler));
        lines.add("Occupants: " + formatOccupants(room.getOccupants(), viewer));
        return lines;
    }

    private String formatExits(Map<Direction, RoomId> exits, Set<Direction> lockedExits) {
        if (exits.isEmpty()) {
            return "none";
        }
        return exits.keySet().stream()
            .sorted(Comparator.comparing(Direction::label))
            .map(dir -> lockedExits.contains(dir) ? dir.label() + " [locked]" : dir.label())
            .collect(Collectors.joining(", "));
    }

    private String formatItems(List<Item> items, TextStyler styler) {
        if (items.isEmpty()) {
            return "none";
        }
        return items.stream()
            .map(item -> styler.rarity(item.presentationName(), item.presentationRarity()))
            .collect(Collectors.joining(", "));
    }

    private String formatOccupants(List<Username> occupants, Username viewer) {
        List<String> names = occupants.stream()
            .filter(username -> !username.equals(viewer))
            .map(Username::getValue)
            .toList();
        if (names.isEmpty()) {
            return "none";
        }
        return String.join(", ", names);
    }
}
