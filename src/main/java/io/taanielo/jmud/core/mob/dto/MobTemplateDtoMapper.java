package io.taanielo.jmud.core.mob.dto;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.DamageType;
import io.taanielo.jmud.core.dialogue.DialogueId;
import io.taanielo.jmud.core.faction.FactionId;
import io.taanielo.jmud.core.mob.GoldDrop;
import io.taanielo.jmud.core.mob.HealerProfile;
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
        int parryChancePercent = dto.parryChance() == null ? 0 : dto.parryChance();
        Map<DamageType, Integer> resistances = toElementalMap(dto.resistances(), dto.id(), "resistances");
        Map<DamageType, Integer> vulnerabilities =
            toElementalMap(dto.vulnerabilities(), dto.id(), "vulnerabilities");
        HealerProfile healerProfile = toHealerProfile(dto);
        Integer enrageTicks = dto.enrageTicks();
        double enrageDamageMultiplier =
            dto.enrageDamageMultiplier() == null ? 1.0 : dto.enrageDamageMultiplier();
        Integer reinforcementHpPercent = dto.reinforcementHpPercent();
        MobId reinforcementMobId =
            dto.reinforcementMobId() != null ? MobId.of(dto.reinforcementMobId()) : null;
        int reinforcementCount = dto.reinforcementCount() == null ? 0 : dto.reinforcementCount();
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
            worldEvent,
            parryChancePercent,
            resistances,
            vulnerabilities,
            healerProfile,
            enrageTicks,
            enrageDamageMultiplier,
            reinforcementHpPercent,
            reinforcementMobId,
            reinforcementCount
        );
    }

    /**
     * Builds the optional {@link HealerProfile} from the raw DTO (issue #733). Returns {@code null}
     * unless the mob is explicitly flagged {@code "healer": true}, keeping every existing mob file a
     * non-healer. A healer mob must author positive {@code heal_min}/{@code heal_max} amounts; the
     * wounded {@code heal_threshold} defaults to {@link HealerProfile#DEFAULT_THRESHOLD_PERCENT} when
     * omitted. Authoring errors surface here as an {@link IllegalArgumentException} at load rather than
     * silently disabling the mechanic.
     */
    private HealerProfile toHealerProfile(MobTemplateDto dto) {
        if (dto.healer() == null || !dto.healer()) {
            return null;
        }
        if (dto.healMin() == null || dto.healMax() == null) {
            throw new IllegalArgumentException("Mob '" + dto.id()
                + "' is flagged healer but is missing heal_min/heal_max");
        }
        int threshold = dto.healThreshold() == null
            ? HealerProfile.DEFAULT_THRESHOLD_PERCENT
            : dto.healThreshold();
        return new HealerProfile(dto.healMin(), dto.healMax(), threshold);
    }

    /**
     * Converts a raw {@code {"fire": 50}} JSON object into a {@link DamageType}-keyed map, resolving
     * each key case-insensitively via {@link DamageType#fromString(String)}. An absent object yields an
     * empty map (today's behaviour). An unrecognised or {@code physical} key is rejected so authoring
     * typos surface at load rather than silently doing nothing.
     */
    private Map<DamageType, Integer> toElementalMap(
        Map<String, Integer> raw, String mobId, String field) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<DamageType, Integer> result = new EnumMap<>(DamageType.class);
        for (Map.Entry<String, Integer> entry : raw.entrySet()) {
            DamageType type = DamageType.fromString(entry.getKey());
            if (type == DamageType.PHYSICAL) {
                throw new IllegalArgumentException("Mob '" + mobId + "' has an invalid " + field
                    + " damage type '" + entry.getKey() + "' (expected fire/cold/poison)");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("Mob '" + mobId + "' has a null " + field
                    + " percent for '" + entry.getKey() + "'");
            }
            result.put(type, entry.getValue());
        }
        return result;
    }
}
