package io.taanielo.jmud.core.world.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * Data transfer object for room persistence. Schema version 2 adds the {@code exits} field;
 * version 3 adds the optional {@code lockedExits} map; version 4 adds the optional
 * {@code minLevel} (serialized as {@code min_level}) field; version 5 adds the optional
 * {@code nightDescription} (serialized as {@code night_description}) field; version 6 adds the
 * optional {@code lightLevel} (serialized as {@code light_level}) field; version 7 adds the
 * optional {@code isOutdoor} (serialized as {@code is_outdoor}) boolean flag; version 8 adds the
 * optional {@code ambientMessages} (serialized as {@code ambient_messages}) array of flavour lines;
 * version 9 adds the optional {@code hiddenExits} (serialized as {@code hidden_exits}) map of secret
 * exits, hidden from the exit listing until discovered via SEARCH.
 * Version 1, 2, 3, 4, 5, 6, 7, and 8 files are still accepted.
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
    @Nullable Integer lightLevel,
    @JsonProperty("is_outdoor") @Nullable Boolean isOutdoor,
    @JsonProperty("ambient_messages") @Nullable List<String> ambientMessages,
    @JsonProperty("hidden_exits") @Nullable Map<String, String> hiddenExits
) {
}
