package io.taanielo.jmud.core.world.dto;

import java.util.List;
import java.util.Map;

/**
 * Data transfer object for room persistence. Schema version 2 adds the {@code exits} field;
 * version 3 adds the optional {@code lockedExits} map.
 * Version 1 and 2 files are still accepted.
 */
public record RoomDto(int schemaVersion, String id, String name, String description, List<String> itemIds, Map<String, String> exits, Map<String, String> lockedExits) {
}
