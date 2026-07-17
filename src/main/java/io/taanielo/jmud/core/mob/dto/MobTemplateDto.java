package io.taanielo.jmud.core.mob.dto;

import java.util.List;
import java.util.Map;

public record MobTemplateDto(
    int schemaVersion,
    String id,
    String name,
    int maxHp,
    String attackId,
    String specialAttackId,
    Boolean aggressive,
    List<String> tags,
    List<LootEntryDto> loot,
    String spawnRoomId,
    int maxCount,
    int respawnTicks,
    Integer xpReward,
    GoldDropDto goldDrop,
    Boolean wanders,
    Integer nightRespawnTicks,
    Integer summonDurationTicks,
    Boolean charmable,
    String dialogueId,
    String factionId,
    Boolean worldBoss,
    Boolean worldEvent,
    Integer parryChance,
    Map<String, Integer> resistances,
    Map<String, Integer> vulnerabilities
) {
}
