package io.taanielo.jmud.core.mob;

import java.util.List;

import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Immutable definition of a mob type loaded from data files.
 * Separate from {@link MobInstance}, which represents a live mob in the world.
 */
public record MobTemplate(
    MobId id,
    String name,
    int maxHp,
    AttackId attackId,
    boolean aggressive,
    List<LootEntry> lootTable,
    RoomId spawnRoomId,
    int maxCount,
    int respawnTicks,
    int xpReward,
    /** Optional gold-drop range; {@code null} means this mob drops no gold. */
    GoldDrop goldDrop,
    /**
     * Optional classification tags (e.g. {@code "undead"}).
     * Always non-null; defaults to an empty list when not specified in data.
     */
    List<String> tags
) {
    public MobTemplate {
        if (maxHp <= 0) {
            throw new IllegalArgumentException("Mob maxHp must be positive");
        }
        if (maxCount <= 0) {
            throw new IllegalArgumentException("Mob maxCount must be positive");
        }
        if (respawnTicks < 0) {
            throw new IllegalArgumentException("Mob respawnTicks must be non-negative");
        }
        if (xpReward < 0) {
            throw new IllegalArgumentException("Mob xpReward must be non-negative");
        }
        lootTable = List.copyOf(lootTable);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /** Returns {@code true} when this mob carries the given tag (case-sensitive). */
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
}
