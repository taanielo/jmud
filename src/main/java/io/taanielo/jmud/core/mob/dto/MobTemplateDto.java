package io.taanielo.jmud.core.mob.dto;

import java.util.List;

public record MobTemplateDto(
    int schemaVersion,
    String id,
    String name,
    int maxHp,
    String attackId,
    List<LootEntryDto> loot,
    String spawnRoomId,
    int maxCount,
    int respawnTicks
) {
}
