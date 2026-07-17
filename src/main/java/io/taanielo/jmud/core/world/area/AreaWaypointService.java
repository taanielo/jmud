package io.taanielo.jmud.core.world.area;

import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Read-only lookups over the world {@link Area} definitions used by the BIND command to resolve a
 * player's recall/respawn anchor.
 *
 * <p>Each area's <em>waypoint</em> is its first room ({@code room_ids[0]} in {@code
 * data/areas/*.json}), the room already used as that area's entrance. BIND only ever anchors to a
 * waypoint, so a player must reach a zone's entrance under their own power before they can bind to
 * it. Every lookup is an in-memory repository read, safe to run on the tick thread (AGENTS.md §5).
 */
@Slf4j
public class AreaWaypointService {

    private final AreaRepository areaRepository;

    /**
     * Creates a waypoint-resolution service backed by the given area repository.
     *
     * @param areaRepository the source of area definitions
     */
    public AreaWaypointService(AreaRepository areaRepository) {
        this.areaRepository = Objects.requireNonNull(areaRepository, "Area repository is required");
    }

    /**
     * Returns the area whose waypoint (first room) is the given room, or empty when the room is not
     * any area's waypoint.
     *
     * @param roomId the room to test
     * @return the area anchored at that room, or {@link Optional#empty()}
     */
    public Optional<Area> findAreaByWaypoint(RoomId roomId) {
        Objects.requireNonNull(roomId, "Room id is required");
        try {
            for (Area area : areaRepository.findAll()) {
                if (!area.roomIds().isEmpty() && area.roomIds().get(0).equals(roomId)) {
                    return Optional.of(area);
                }
            }
        } catch (RepositoryException e) {
            log.warn("Failed to resolve waypoint area for room {}: {}", roomId.getValue(), e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Returns {@code true} when the given room is the waypoint (first room) of some area.
     *
     * @param roomId the room to test
     * @return {@code true} if the room is an area waypoint
     */
    public boolean isWaypoint(RoomId roomId) {
        return findAreaByWaypoint(roomId).isPresent();
    }
}
