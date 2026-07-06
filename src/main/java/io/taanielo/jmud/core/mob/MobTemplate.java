package io.taanielo.jmud.core.mob;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.TimeOfDay;

/**
 * Immutable definition of a mob type loaded from data files.
 * Separate from {@link MobInstance}, which represents a live mob in the world.
 */
public record MobTemplate(
    MobId id,
    String name,
    int maxHp,
    AttackId attackId,
    /**
     * Optional reference to a rarer, harder-hitting attack (e.g. a boss's "troll smash")
     * that mob AI may use at most once per combat encounter instead of {@link #attackId()}.
     * {@code null} means this mob has no special ability.
     */
    AttackId specialAttackId,
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
    List<String> tags,
    /**
     * When {@code true} the mob may randomly wander between linked rooms on each tick.
     * NPCs (tag {@code "npc"}) never wander regardless of this flag.
     * Defaults to {@code false}.
     */
    boolean wanders,
    /**
     * Optional respawn delay (in ticks) used instead of {@link #respawnTicks} when the world is
     * in {@link TimeOfDay#NIGHT}; {@code null} means the mob respawns at the same rate day and
     * night.
     */
    @Nullable Integer nightRespawnTicks
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
        if (nightRespawnTicks != null && nightRespawnTicks < 0) {
            throw new IllegalArgumentException("Mob nightRespawnTicks must be non-negative");
        }
        lootTable = List.copyOf(lootTable);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /**
     * Convenience constructor for callers that don't need a night-specific respawn rate; defaults
     * {@link #nightRespawnTicks()} to {@code null} (same respawn rate day and night).
     */
    public MobTemplate(
        MobId id,
        String name,
        int maxHp,
        AttackId attackId,
        AttackId specialAttackId,
        boolean aggressive,
        List<LootEntry> lootTable,
        RoomId spawnRoomId,
        int maxCount,
        int respawnTicks,
        int xpReward,
        GoldDrop goldDrop,
        List<String> tags,
        boolean wanders
    ) {
        this(id, name, maxHp, attackId, specialAttackId, aggressive, lootTable, spawnRoomId, maxCount,
            respawnTicks, xpReward, goldDrop, tags, wanders, null);
    }

    /** Returns {@code true} when this mob carries the given tag (case-sensitive). */
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /**
     * Returns the respawn delay (in ticks) that applies for the given time of day: uses
     * {@link #nightRespawnTicks} when {@code timeOfDay} is {@link TimeOfDay#NIGHT} and one is
     * configured, otherwise falls back to {@link #respawnTicks}.
     *
     * @param timeOfDay the current time of day
     * @return the respawn delay in ticks to use
     */
    public int respawnTicks(TimeOfDay timeOfDay) {
        if (timeOfDay == TimeOfDay.NIGHT && nightRespawnTicks != null) {
            return nightRespawnTicks;
        }
        return respawnTicks;
    }
}
