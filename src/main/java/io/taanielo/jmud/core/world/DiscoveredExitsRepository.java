package io.taanielo.jmud.core.world;

import java.util.Map;
import java.util.Set;

/**
 * Persistence port for world-scoped hidden-exit discovery state.
 *
 * <p>When a player SEARCHes a hidden exit into the open it becomes visible and walkable for every
 * player, permanently — including across server restarts. This port persists that discovery so the
 * promise survives a restart: which {@link RoomId} has had which {@link Direction}s revealed.
 *
 * <p>Implementations load all persisted discoveries once at startup and persist per-room discovery
 * sets without blocking the tick thread (write-behind, AGENTS.md §5). The authoritative in-memory
 * state is owned by {@link PlayerLocationService}; this port only reads it at boot and writes
 * snapshots back on the rare event of a new discovery.
 */
public interface DiscoveredExitsRepository {

    /**
     * Loads every persisted hidden-exit discovery, grouped by the room the exit belongs to.
     *
     * <p>Called once at startup on the bootstrap thread. Rooms with no discovered exits are absent
     * from the returned map. A missing, empty, or malformed store yields an empty map so no exit is
     * ever accidentally revealed.
     *
     * @return a map from room id to the set of discovered hidden-exit directions (never {@code null})
     */
    Map<RoomId, Set<Direction>> loadAll();

    /**
     * Persists the complete set of discovered hidden-exit directions for a single room, replacing
     * any previously stored set for that room.
     *
     * <p>Implementations must perform the actual disk write off the tick thread (write-behind), so
     * this call is safe to invoke during command execution. It is only ever called on the rare
     * discovery event, never on a per-move or per-tick hot path.
     *
     * @param roomId     the room whose discovered exits to persist
     * @param directions the full, current set of discovered hidden-exit directions for that room
     */
    void save(RoomId roomId, Set<Direction> directions);

    /**
     * Returns a no-op repository that persists nothing and loads nothing.
     *
     * <p>Used by call sites (chiefly tests and the legacy two-argument
     * {@link PlayerLocationService} constructor) that do not need durable discovery state; behaviour
     * is identical to the pre-persistence in-session-only model.
     *
     * @return a stateless no-op repository (never {@code null})
     */
    static DiscoveredExitsRepository noOp() {
        return new DiscoveredExitsRepository() {
            @Override
            public Map<RoomId, Set<Direction>> loadAll() {
                return Map.of();
            }

            @Override
            public void save(RoomId roomId, Set<Direction> directions) {
                // Intentionally no-op: discoveries live only in the service's runtime map.
            }
        };
    }
}
