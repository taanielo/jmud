package io.taanielo.jmud.core.world.dto;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Data transfer object for room persistence. Schema version 2 adds the {@code exits} field;
 * version 3 adds the optional {@code lockedExits} map; version 4 adds the optional
 * {@code minLevel} (serialized as {@code min_level}) field; version 5 adds the optional
 * {@code nightDescription} (serialized as {@code night_description}) field; version 6 adds the
 * optional {@code lightLevel} (serialized as {@code light_level}) field.
 * Version 1, 2, 3, 4, and 5 files are still accepted.
 */
public record RoomDto(
    int schemaVersion,
    String id,
    String name,
    String description,
    List<String> itemIds,
    Map<String, String> exits,
    Map<String, String> lockedExits,
    @Nullable Integer minLevel,
    @Nullable String nightDescription,
    @Nullable Integer lightLevel
) {
}
