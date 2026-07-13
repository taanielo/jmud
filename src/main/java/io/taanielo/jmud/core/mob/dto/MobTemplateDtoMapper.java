package io.taanielo.jmud.core.mob.dto;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.dialogue.DialogueId;
import io.taanielo.jmud.core.faction.FactionId;
import io.taanielo.jmud.core.mob.GoldDrop;
import io.taanielo.jmud.core.mob.LootEntry;
import io.taanielo.jmud.core.mob.MobId;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.world.ItemId;
import io.taanielo.jmud.core.world.RoomId;

public class MobTemplateDtoMapper {

    public MobTemplate toDomain(MobTemplateDto dto) {
        Objects.requireNonNull(dto, "MobTemplateDto is required");
        AttackId attackId = dto.attackId() != null ? AttackId.of(dto.attackId()) : null;
        AttackId specialAttackId = dto.specialAttackId() != null ? AttackId.of(dto.specialAttackId()) : null;
        List<LootEntry> loot = dto.loot() == null ? List.of() : dto.loot().stream()
            .map(e -> new LootEntry(ItemId.of(e.itemId()), e.dropChance()))
            .toList();
        boolean aggressive = dto.aggressive() == null || dto.aggressive();
        if (dto.xpReward() == null) {
            throw new IllegalArgumentException("Mob '" + dto.id()
                + "' is missing an explicit xp_reward (docs/content-dod.md → Mob); "
                + "set it to 0 for non-combatant NPCs");
        }
        int xpReward = dto.xpReward();
        GoldDrop goldDrop = dto.goldDrop() != null
            ? new GoldDrop(dto.goldDrop().min(), dto.goldDrop().max())
            : null;
        List<String> tags = dto.tags() == null ? List.of() : List.copyOf(dto.tags());
        boolean wanders = dto.wanders() != null && dto.wanders();
        boolean charmable = dto.charmable() != null && dto.charmable();
        DialogueId dialogueId = dto.dialogueId() != null ? DialogueId.of(dto.dialogueId()) : null;
        FactionId factionId = dto.factionId() != null ? FactionId.of(dto.factionId()) : null;
        boolean worldBoss = dto.worldBoss() != null && dto.worldBoss();
        boolean worldEvent = dto.worldEvent() != null && dto.worldEvent();
        return new MobTemplate(
            MobId.of(dto.id()),
            dto.name(),
            dto.maxHp(),
            attackId,
            specialAttackId,
            aggressive,
            loot,
            RoomId.of(dto.spawnRoomId()),
            dto.maxCount(),
            dto.respawnTicks(),
            xpReward,
            goldDrop,
            tags,
            wanders,
            dto.nightRespawnTicks(),
            dto.summonDurationTicks(),
            charmable,
            dialogueId,
            factionId,
            worldBoss,
            worldEvent
        );
    }
}
