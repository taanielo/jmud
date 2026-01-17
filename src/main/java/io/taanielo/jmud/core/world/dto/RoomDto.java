package io.taanielo.jmud.core.world.dto;

import java.util.List;

public record RoomDto(int schemaVersion, String id, String name, String description, List<String> itemIds) {
}
