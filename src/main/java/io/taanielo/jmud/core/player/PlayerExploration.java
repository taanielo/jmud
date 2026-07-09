package io.taanielo.jmud.core.player;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.taanielo.jmud.core.world.RoomId;

/**
 * Tracks the set of rooms a player has previously visited (explored).
 *
 * <p>Exploration is used to render the player's personal minimap (see the MAP command): a player
 * may only see rooms they have entered at least once. The visited set is persisted on the
 * {@link Player} as {@code explored_rooms} so discovered territory survives logout/login.
 *
 * <p>The set preserves insertion order (first-visited first) so serialization is deterministic,
 * which keeps save files stable and tests reproducible.
 */
public class PlayerExploration {

    private final Set<RoomId> visited;

    /**
     * Creates an exploration component from the given room-id strings. {@code null} entries and
     * blanks are ignored; duplicates collapse. A {@code null} list yields an empty component.
     *
     * @param roomIds the visited room id strings, in first-visited order
     */
    public PlayerExploration(List<String> roomIds) {
        Set<RoomId> parsed = new LinkedHashSet<>();
        if (roomIds != null) {
            for (String roomId : roomIds) {
                if (roomId != null && !roomId.isBlank()) {
                    parsed.add(RoomId.of(roomId));
                }
            }
        }
        this.visited = parsed;
    }

    private PlayerExploration(Set<RoomId> visited) {
        this.visited = visited;
    }

    /**
     * Returns an empty exploration component (no rooms visited).
     */
    public static PlayerExploration empty() {
        return new PlayerExploration(new LinkedHashSet<>());
    }

    /**
     * Returns an unmodifiable view of the visited rooms, in first-visited order.
     */
    public Set<RoomId> visited() {
        return Set.copyOf(visited);
    }

    /**
     * Returns whether the given room has been visited by this player.
     *
     * @param roomId the room to test; must not be null
     * @return {@code true} if the room is in the visited set
     */
    public boolean hasVisited(RoomId roomId) {
        Objects.requireNonNull(roomId, "Room id is required");
        return visited.contains(roomId);
    }

    /**
     * Returns how many distinct rooms the player has explored.
     */
    public int count() {
        return visited.size();
    }

    /**
     * Returns a copy of this component with the given room marked as visited, or this instance
     * unchanged when the room was already visited.
     *
     * @param roomId the room the player has entered; must not be null
     */
    public PlayerExploration visit(RoomId roomId) {
        Objects.requireNonNull(roomId, "Room id is required");
        if (visited.contains(roomId)) {
            return this;
        }
        Set<RoomId> next = new LinkedHashSet<>(visited);
        next.add(roomId);
        return new PlayerExploration(next);
    }

    /**
     * Returns the visited rooms as an ordered list of their id strings, for JSON persistence.
     */
    public List<String> toIdList() {
        return visited.stream().map(RoomId::getValue).toList();
    }
}
