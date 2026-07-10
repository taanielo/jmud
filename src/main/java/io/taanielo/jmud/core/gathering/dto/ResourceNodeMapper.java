package io.taanielo.jmud.core.gathering.dto;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.gathering.ResourceNode;
import io.taanielo.jmud.core.gathering.ResourceNodeId;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Maps {@link ResourceNodeDto} JSON transfer objects to {@link ResourceNode} domain value objects.
 */
public class ResourceNodeMapper {

    /**
     * Converts a resource-node DTO into its domain form, validating required fields.
     *
     * @param dto the node DTO read from JSON
     * @return the domain resource node
     * @throws IllegalArgumentException when a required field is missing or invalid
     */
    public ResourceNode toDomain(ResourceNodeDto dto) {
        Objects.requireNonNull(dto, "Resource node DTO is required");
        String id = requireText(dto.id(), "id");
        String roomId = requireText(dto.roomId(), "room_id");
        String yieldItem = requireText(dto.yieldItem(), "yield_item");
        String name = requireText(dto.name(), "name");
        String lookDescription = requireText(dto.lookDescription(), "look_description");
        if (dto.respawnTicks() == null) {
            throw new IllegalArgumentException("Resource node field 'respawn_ticks' is required");
        }
        return new ResourceNode(
            ResourceNodeId.of(id),
            RoomId.of(roomId),
            ItemId.of(yieldItem),
            dto.respawnTicks(),
            name,
            lookDescription);
    }

    private String requireText(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Resource node field '" + field + "' is required");
        }
        return value;
    }
}
