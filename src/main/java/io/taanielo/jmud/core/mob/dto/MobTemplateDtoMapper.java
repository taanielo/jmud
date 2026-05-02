package io.taanielo.jmud.core.mob.dto;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.mob.LootEntry;
import io.taanielo.jmud.core.mob.MobId;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.RoomId;

public class MobTemplateDtoMapper {

    public MobTemplate toDomain(MobTemplateDto dto) {
        Objects.requireNonNull(dto, "MobTemplateDto is required");
        AttackId attackId = dto.attackId() != null ? AttackId.of(dto.attackId()) : null;
        List<LootEntry> loot = dto.loot() == null ? List.of() : dto.loot().stream()
            .map(e -> new LootEntry(ItemId.of(e.itemId()), e.dropChance()))
            .toList();
        return new MobTemplate(
            MobId.of(dto.id()),
            dto.name(),
            dto.maxHp(),
            attackId,
            loot,
            RoomId.of(dto.spawnRoomId()),
            dto.maxCount(),
            dto.respawnTicks()
        );
    }
}
