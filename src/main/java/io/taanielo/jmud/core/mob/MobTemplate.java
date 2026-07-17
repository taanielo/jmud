package io.taanielo.jmud.core.mob;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.dialogue.DialogueId;
import io.taanielo.jmud.core.faction.FactionId;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.TimeOfDay;

/**
 * Immutable definition of a mob type loaded from data files.
 * Separate from {@link MobInstance}, which represents a live mob in the world.
 *
 * @param specialAttackId optional reference to a rarer, harder-hitting attack (e.g. a boss's "troll
 *                        smash") that mob AI may use at most once per combat encounter instead of
 *                        {@link #attackId()}; {@code null} means this mob has no special ability
 * @param goldDrop optional gold-drop range; {@code null} means this mob drops no gold
 * @param tags optional classification tags (e.g. {@code "undead"}); always non-null, defaults to an
 *            empty list when not specified in data
 * @param wanders when {@code true} the mob may randomly wander between linked rooms on each tick;
 *              NPCs (tag {@code "npc"}) never wander regardless of this flag; defaults to
 *              {@code false}
 * @param nightRespawnTicks optional respawn delay (in ticks) used instead of {@link #respawnTicks()}
 *                         when the world is in {@link TimeOfDay#NIGHT}; {@code null} means the mob
 *                         respawns at the same rate day and night
 * @param summonDurationTicks optional lifetime (in ticks) of a summoned pet spawned from this
 *                           template (see the necromancer-style SUMMON spell); when non-null this
 *                           template is a <em>pet template</em>: it is never spawned into the world
 *                           at start-up and never respawns — an instance exists only while a player's
 *                           summon is active, and it is removed the moment it dies or this many ticks
 *                           elapse; {@code null} for ordinary world mobs
 * @param charmable when {@code true} the mob may be permanently captured as a companion via the TAME
 *                 command (see the pet/charm system); defaults to {@code false} — ordinary mobs
 *                 cannot be tamed
 * @param dialogueId optional id of a dialogue tree this NPC offers via the {@code TALK} command;
 *                  {@code null} when the mob has no conversation; the referenced tree is loaded from
 *                  {@code data/dialogues/}
 * @param factionId optional id of a faction this mob belongs to; {@code null} for faction-neutral
 *                 mobs; slaying a faction mob shifts the killer's reputation with the faction, and
 *                 the faction's hostility rules govern whether the mob engages a given player (see
 *                 the reputation system)
 * @param worldBoss when {@code true} this mob is a <em>world boss</em>: a rare, powerful encounter
 *                 whose spawn and death are announced to every online player, and whose loot
 *                 resolution guarantees at least one rare-or-higher item drop on top of the normal
 *                 loot table; defaults to {@code false} for ordinary mobs, which produce no such
 *                 server-wide announcements
 * @param worldEvent when {@code true} this mob is a <em>world-event</em> encounter: a rare elite
 *                  spawned on-demand by the {@link WorldEventScheduler} rather than at server start.
 *                  Like a pet template it is excluded from {@link MobRegistry}'s normal start-up
 *                  spawn loop and never auto-respawns; an instance exists only for the bounded
 *                  window a scheduled world event is open. Combine with {@link #worldBoss()} to reuse
 *                  the world-boss kill path (server-wide death announcement and guaranteed
 *                  rare-or-higher drop). Defaults to {@code false} for ordinary mobs.
 * @param parryChancePercent percentage chance, in {@code [0, 100]}, that this mob parries an
 *                  otherwise-landing player <em>melee</em> attack — fully negating that swing's
 *                  damage and riposting the attacker with its own attack. Only a subset of
 *                  deliberately authored, defensively trained melee mobs (armoured guards, trained
 *                  humanoid combatants, boss-tier elites) carry a non-zero value; it defaults to
 *                  {@code 0} so ordinary mobs never parry and existing data is unaffected. Ranged,
 *                  AoE-spell, and summoned-pet damage against the mob never rolls this check. The
 *                  effective chance is clamped to
 *                  {@code [CombatSettings.MIN_PARRY_CHANCE, CombatSettings.MAX_PARRY_CHANCE]} at
 *                  resolution time, mirroring the player-side parry bound.
 */
public record MobTemplate(
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
    boolean wanders,
    @Nullable Integer nightRespawnTicks,
    @Nullable Integer summonDurationTicks,
    boolean charmable,
    @Nullable DialogueId dialogueId,
    @Nullable FactionId factionId,
    boolean worldBoss,
    boolean worldEvent,
    int parryChancePercent
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
        if (summonDurationTicks != null && summonDurationTicks <= 0) {
            throw new IllegalArgumentException("Mob summonDurationTicks must be positive");
        }
        if (parryChancePercent < 0 || parryChancePercent > 100) {
            throw new IllegalArgumentException("Mob parryChancePercent must be in [0, 100]");
        }
        lootTable = List.copyOf(lootTable);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /**
     * Convenience constructor for callers that don't need a night-specific respawn rate; defaults
     * {@link #nightRespawnTicks()} and {@link #summonDurationTicks()} to {@code null} (an ordinary
     * world mob with the same respawn rate day and night).
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
            respawnTicks, xpReward, goldDrop, tags, wanders, null, null, false, null, null, false, false);
    }

    /**
     * Convenience constructor for callers that specify a night-specific respawn rate but are not
     * pet templates; defaults {@link #summonDurationTicks()} to {@code null}.
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
        boolean wanders,
        @Nullable Integer nightRespawnTicks
    ) {
        this(id, name, maxHp, attackId, specialAttackId, aggressive, lootTable, spawnRoomId, maxCount,
            respawnTicks, xpReward, goldDrop, tags, wanders, nightRespawnTicks, null, false, null, null, false,
            false);
    }

    /**
     * Convenience constructor for pet templates and other callers that specify a night-specific
     * respawn rate and a summon duration but are not charmable; defaults {@link #charmable()} to
     * {@code false}.
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
        boolean wanders,
        @Nullable Integer nightRespawnTicks,
        @Nullable Integer summonDurationTicks
    ) {
        this(id, name, maxHp, attackId, specialAttackId, aggressive, lootTable, spawnRoomId, maxCount,
            respawnTicks, xpReward, goldDrop, tags, wanders, nightRespawnTicks, summonDurationTicks, false, null,
            null, false, false);
    }

    /**
     * Convenience constructor for callers that specify a charmable flag but no dialogue tree;
     * defaults {@link #dialogueId()} to {@code null} (an NPC with no {@code TALK} conversation).
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
        boolean wanders,
        @Nullable Integer nightRespawnTicks,
        @Nullable Integer summonDurationTicks,
        boolean charmable
    ) {
        this(id, name, maxHp, attackId, specialAttackId, aggressive, lootTable, spawnRoomId, maxCount,
            respawnTicks, xpReward, goldDrop, tags, wanders, nightRespawnTicks, summonDurationTicks,
            charmable, null, null, false, false);
    }

    /**
     * Convenience constructor for callers that specify a dialogue tree and faction but are not world
     * bosses; defaults {@link #worldBoss()} to {@code false} (an ordinary mob with no server-wide
     * spawn/death announcements).
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
        boolean wanders,
        @Nullable Integer nightRespawnTicks,
        @Nullable Integer summonDurationTicks,
        boolean charmable,
        @Nullable DialogueId dialogueId,
        @Nullable FactionId factionId
    ) {
        this(id, name, maxHp, attackId, specialAttackId, aggressive, lootTable, spawnRoomId, maxCount,
            respawnTicks, xpReward, goldDrop, tags, wanders, nightRespawnTicks, summonDurationTicks,
            charmable, dialogueId, factionId, false, false);
    }

    /**
     * Convenience constructor for callers that specify a world-boss flag but are not world-event
     * encounters; defaults {@link #worldEvent()} to {@code false} (an ordinary mob or permanent
     * world boss spawned at server start rather than by the {@link WorldEventScheduler}).
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
        boolean wanders,
        @Nullable Integer nightRespawnTicks,
        @Nullable Integer summonDurationTicks,
        boolean charmable,
        @Nullable DialogueId dialogueId,
        @Nullable FactionId factionId,
        boolean worldBoss
    ) {
        this(id, name, maxHp, attackId, specialAttackId, aggressive, lootTable, spawnRoomId, maxCount,
            respawnTicks, xpReward, goldDrop, tags, wanders, nightRespawnTicks, summonDurationTicks,
            charmable, dialogueId, factionId, worldBoss, false, 0);
    }

    /**
     * Convenience constructor for callers that specify a world-event flag but do not fight
     * defensively; defaults {@link #parryChancePercent()} to {@code 0} (a mob that never parries a
     * player's melee attack). This preserves the pre-parry constructor arity so existing callers and
     * the JSON mapper remain source-compatible.
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
        boolean wanders,
        @Nullable Integer nightRespawnTicks,
        @Nullable Integer summonDurationTicks,
        boolean charmable,
        @Nullable DialogueId dialogueId,
        @Nullable FactionId factionId,
        boolean worldBoss,
        boolean worldEvent
    ) {
        this(id, name, maxHp, attackId, specialAttackId, aggressive, lootTable, spawnRoomId, maxCount,
            respawnTicks, xpReward, goldDrop, tags, wanders, nightRespawnTicks, summonDurationTicks,
            charmable, dialogueId, factionId, worldBoss, worldEvent, 0);
    }

    /**
     * Returns whether this mob is authored to fight defensively — i.e. carries a non-zero
     * {@link #parryChancePercent()} and can therefore parry a player's melee attack.
     *
     * @return {@code true} when {@link #parryChancePercent()} is positive
     */
    public boolean canParry() {
        return parryChancePercent > 0;
    }

    /** Returns {@code true} when this mob carries the given tag (case-sensitive). */
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /**
     * Returns whether this template describes a summonable pet (see {@link #summonDurationTicks()}).
     * Pet templates are excluded from world start-up spawning and from respawn; an instance exists
     * only for the lifetime of an active player summon.
     *
     * @return {@code true} when {@link #summonDurationTicks()} is set
     */
    public boolean isPetTemplate() {
        return summonDurationTicks != null;
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
