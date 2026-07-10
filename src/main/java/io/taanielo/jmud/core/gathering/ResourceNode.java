package io.taanielo.jmud.core.gathering;

import java.util.Objects;

import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Immutable definition of a harvestable resource node placed in the world (an ore vein, a herb
 * patch, etc.).
 *
 * <p>A node ties a raw-material {@link ItemId yield item} to a specific {@link RoomId room} and
 * declares how many ticks it takes to respawn after being harvested. Instances are static content
 * loaded from {@code data/resource-nodes/*.json}; the mutable depletion state is tracked separately
 * by {@link ResourceGatheringService} so this record stays a pure value object (mirroring the
 * {@code Corpse}/{@code RoomItemService} split).
 *
 * @param id              the unique node id
 * @param roomId          the room the node lives in
 * @param yieldItemId     the id of the raw-material item added to the harvester's inventory
 * @param respawnTicks    the number of ticks a depleted node takes to become available again;
 *                        must be positive
 * @param name            the short node label (no leading article), e.g. {@code "iron ore vein"}
 * @param lookDescription the full sentence shown in the room description while the node is available
 */
public record ResourceNode(
    ResourceNodeId id,
    RoomId roomId,
    ItemId yieldItemId,
    int respawnTicks,
    String name,
    String lookDescription
) {

    public ResourceNode {
        Objects.requireNonNull(id, "Node id is required");
        Objects.requireNonNull(roomId, "Room id is required");
        Objects.requireNonNull(yieldItemId, "Yield item id is required");
        Objects.requireNonNull(name, "Name is required");
        Objects.requireNonNull(lookDescription, "Look description is required");
        if (respawnTicks <= 0) {
            throw new IllegalArgumentException("Respawn ticks must be positive");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("Name must not be blank");
        }
        if (lookDescription.isBlank()) {
            throw new IllegalArgumentException("Look description must not be blank");
        }
    }
}
