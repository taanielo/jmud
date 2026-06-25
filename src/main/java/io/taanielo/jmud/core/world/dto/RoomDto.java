package io.taanielo.jmud.core.world.dto;

import java.util.List;
import java.util.Map;

/**
 * Data transfer object for room persistence. Schema version 2 adds the {@code exits} field;
 * version 1 files are still accepted and will produce rooms with no exits.
 */
public record RoomDto(int schemaVersion, String id, String name, String description, List<String> itemIds, Map<String, String> exits) {
}
