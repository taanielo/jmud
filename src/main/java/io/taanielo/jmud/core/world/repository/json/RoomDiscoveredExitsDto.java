package io.taanielo.jmud.core.world.repository.json;

import java.util.List;

/**
 * JSON transfer object for a single room's discovered hidden exits inside the world discovery store.
 *
 * @param roomId     the room whose hidden exits were discovered
 * @param directions the discovered hidden-exit directions, stored as {@link
 *                   io.taanielo.jmud.core.world.Direction} enum names (e.g. {@code "NORTH"})
 */
record RoomDiscoveredExitsDto(
    String roomId,
    List<String> directions
) {
}
