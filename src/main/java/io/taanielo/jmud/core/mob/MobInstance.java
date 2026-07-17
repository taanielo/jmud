package io.taanielo.jmud.core.mob;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.TimeOfDay;

/**
 * A live mob instance in the world.
 *
 * <p>HP is tracked via {@link AtomicInteger} so that player-thread attacks
 * (command queue) and tick-thread AI reads are both safe. All other state
 * mutations are confined to the tick thread.
 *
 * <p>{@code currentRoomId} tracks the mob's live position (it starts at
 * {@code template.spawnRoomId()} and is updated when the mob wanders or
 * reset to the spawn room on respawn).
 */
public class MobInstance {

    private final UUID instanceId = UUID.randomUUID();
    private final MobTemplate template;
    private final AtomicInteger hp;
    private final AtomicInteger respawnTicksRemaining = new AtomicInteger(0);
    private final Set<Username> engagedPlayers = ConcurrentHashMap.newKeySet();
    /**
     * Whether this mob has already used its {@link MobTemplate#specialAttackId()} in the
     * current combat encounter. Reset whenever the encounter ends (see {@link #disengage(Username)}
     * and {@link #respawn()}).
     */
    private final AtomicBoolean specialAbilityUsed = new AtomicBoolean(false);
    /**
     * The player currently forcing this mob's aggro via the Warrior TAUNT skill, or {@code null}
     * when no taunt is active. Paired with {@link #tauntTicksRemaining}: the taunt is only honoured
     * while the counter is positive. Mutated exclusively through {@link MobRegistry} on the tick
     * thread; cleared when the taunter disengages ({@link #disengage(Username)}) or on
     * {@link #respawn()}, mirroring {@link #specialAbilityUsed}.
     */
    private final AtomicReference<Username> taunter = new AtomicReference<>();
    /**
     * Remaining number of AI decisions for which {@link #taunter} holds this mob's aggro. Decremented
     * on the tick thread via {@link #consumeTauntTick()}; when it reaches zero the taunt expires and
     * the mob resumes normal random targeting.
     */
    private final AtomicInteger tauntTicksRemaining = new AtomicInteger(0);
    /** Mutable live location; confined to tick thread for writes, safe to read from any thread. */
    private volatile RoomId currentRoomId;
    /**
     * The player who summoned this mob, or {@code null} for ordinary world mobs. A summoned pet
     * fights alongside its summoner and never attacks them (see {@link #isSummoned()}).
     */
    @Nullable
    private final Username summoner;
    /**
     * Remaining lifetime, in ticks, of a summoned pet before it auto-dismisses. Only meaningful when
     * {@link #summoner} is non-null; decremented on the tick thread via {@link #tickSummonLifetime()}.
     */
    private final AtomicInteger summonTicksRemaining = new AtomicInteger(0);
    /**
     * The player who permanently tamed this mob (see the TAME command), or {@code null} for
     * ordinary world mobs and temporary summons. Unlike {@link #summoner}, a tamed pet has no
     * lifetime: it persists across sessions, follows its owner between rooms, and fights alongside
     * them until it is dismissed or destroyed.
     */
    @Nullable
    private final Username owner;
    /**
     * The owner-assigned custom display name of a tamed companion (see the NAME command), or
     * {@code null} when the companion is unnamed and shows its template name. Only meaningful for
     * {@link #isTamed() tamed} pets; written on the tick thread via {@link #setCustomName(String)}
     * (naming and respawn-on-login), read from any thread for message construction.
     */
    @Nullable
    private volatile String customName;
    /**
     * The owner-assigned custom roleplay description of a tamed companion (see the DESCRIBE command),
     * or {@code null} when the companion has no custom description. Only meaningful for
     * {@link #isTamed() tamed} pets; written on the tick thread via {@link #setDescription(String)}
     * (describing and respawn-on-login), read from any thread for LOOK rendering. When {@code null}
     * a LOOK falls back to a generic line rather than showing this description.
     */
    @Nullable
    private volatile String customDescription;

    public MobInstance(MobTemplate template) {
        this.template = template;
        this.hp = new AtomicInteger(template.maxHp());
        this.currentRoomId = template.spawnRoomId();
        this.summoner = null;
        this.owner = null;
    }

    private MobInstance(MobTemplate template, RoomId spawnRoom, Username summoner, int durationTicks) {
        this.template = Objects.requireNonNull(template, "Template is required");
        this.hp = new AtomicInteger(template.maxHp());
        this.currentRoomId = Objects.requireNonNull(spawnRoom, "Spawn room is required");
        this.summoner = Objects.requireNonNull(summoner, "Summoner is required");
        this.owner = null;
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("Summon duration must be positive");
        }
        this.summonTicksRemaining.set(durationTicks);
    }

    private MobInstance(MobTemplate template, RoomId spawnRoom, Username owner) {
        this.template = Objects.requireNonNull(template, "Template is required");
        this.hp = new AtomicInteger(template.maxHp());
        this.currentRoomId = Objects.requireNonNull(spawnRoom, "Spawn room is required");
        this.summoner = null;
        this.owner = Objects.requireNonNull(owner, "Owner is required");
    }

    /**
     * Creates a summoned pet instance owned by {@code summoner}, spawned into {@code spawnRoom} and
     * living for {@code durationTicks} ticks. The pet fights hostile mobs in its room on the caster's
     * behalf and is removed on death or when its lifetime elapses.
     *
     * @param template      the pet template (must be a {@link MobTemplate#isPetTemplate() pet template})
     * @param spawnRoom     the room to spawn the pet into (normally the summoner's current room)
     * @param summoner      the player who summoned the pet
     * @param durationTicks how many ticks the pet lives before auto-dismissing; must be positive
     * @return the new summoned pet instance
     */
    public static MobInstance summoned(
        MobTemplate template, RoomId spawnRoom, Username summoner, int durationTicks) {
        return new MobInstance(template, spawnRoom, summoner, durationTicks);
    }

    /**
     * Creates a permanently tamed pet instance owned by {@code owner}, spawned into {@code spawnRoom}.
     * Unlike a {@link #summoned} pet, a tamed pet never expires: it follows its owner between rooms
     * and fights hostile mobs at their side until dismissed or destroyed.
     *
     * @param template  the tamed mob's template
     * @param spawnRoom the room to spawn the pet into (normally the owner's current room)
     * @param owner     the player who tamed the mob
     * @return the new tamed pet instance
     */
    public static MobInstance tamed(MobTemplate template, RoomId spawnRoom, Username owner) {
        return new MobInstance(template, spawnRoom, owner);
    }

    /**
     * Creates a permanently tamed pet instance owned by {@code owner} and carrying a custom display
     * name (see the NAME command). Used when respawning a persisted companion whose owner has already
     * named it, so the custom name is present from the moment it re-enters the world.
     *
     * @param template   the tamed mob's template
     * @param spawnRoom  the room to spawn the pet into (normally the owner's current room)
     * @param owner      the player who tamed the mob
     * @param customName the companion's custom display name, or {@code null} to keep the template name
     * @return the new tamed pet instance
     */
    public static MobInstance tamed(
        MobTemplate template, RoomId spawnRoom, Username owner, @Nullable String customName) {
        return tamed(template, spawnRoom, owner, customName, null);
    }

    /**
     * Creates a permanently tamed pet instance owned by {@code owner} and carrying an optional custom
     * display name (see the NAME command) and custom roleplay description (see the DESCRIBE command).
     * Used when respawning a persisted companion whose owner has already named and/or described it, so
     * both are present from the moment it re-enters the world.
     *
     * @param template          the tamed mob's template
     * @param spawnRoom         the room to spawn the pet into (normally the owner's current room)
     * @param owner             the player who tamed the mob
     * @param customName        the companion's custom display name, or {@code null} to keep the
     *                          template name
     * @param customDescription the companion's custom roleplay description, or {@code null} to fall
     *                          back to the generic LOOK line
     * @return the new tamed pet instance
     */
    public static MobInstance tamed(
        MobTemplate template,
        RoomId spawnRoom,
        Username owner,
        @Nullable String customName,
        @Nullable String customDescription) {
        MobInstance instance = new MobInstance(template, spawnRoom, owner);
        if (customName != null && !customName.isBlank()) {
            instance.customName = customName;
        }
        if (customDescription != null && !customDescription.isBlank()) {
            instance.customDescription = customDescription;
        }
        return instance;
    }

    public UUID instanceId() {
        return instanceId;
    }

    public MobTemplate template() {
        return template;
    }

    /** Returns the mob's current live room (may differ from the template spawn room after wandering). */
    public RoomId roomId() {
        return currentRoomId;
    }

    /**
     * Moves the mob to the given room.
     * Must only be called from the tick thread.
     *
     * @param roomId the destination room
     */
    public void moveTo(RoomId roomId) {
        this.currentRoomId = Objects.requireNonNull(roomId, "Room id is required");
    }

    public boolean isAlive() {
        return hp.get() > 0;
    }

    public int currentHp() {
        return hp.get();
    }

    /** Applies damage and returns remaining HP (clamped to 0). */
    public int takeDamage(int amount) {
        return hp.updateAndGet(current -> Math.max(0, current - amount));
    }

    /**
     * Called once when the mob dies — starts the respawn countdown, using the day or night
     * respawn delay from {@link MobTemplate#respawnTicks(TimeOfDay)} as appropriate.
     *
     * @param timeOfDay the current time of day, used to pick the respawn delay
     */
    public void scheduleRespawn(TimeOfDay timeOfDay) {
        respawnTicksRemaining.set(template.respawnTicks(timeOfDay));
    }

    /**
     * Decrements the respawn countdown.
     *
     * @return true when the countdown reaches zero and the mob should respawn
     */
    public boolean tickRespawn() {
        return respawnTicksRemaining.decrementAndGet() <= 0;
    }

    public void engage(Username player) {
        engagedPlayers.add(player);
    }

    public void disengage(Username player) {
        engagedPlayers.remove(player);
        if (player.equals(taunter.get())) {
            clearTaunt();
        }
        if (engagedPlayers.isEmpty()) {
            specialAbilityUsed.set(false);
            clearTaunt();
        }
    }

    public Set<Username> engagedPlayers() {
        return Collections.unmodifiableSet(engagedPlayers);
    }

    /**
     * Returns whether this mob has already used its special ability
     * (see {@link MobTemplate#specialAttackId()}) in the current combat encounter.
     *
     * @return {@code true} if the special ability has already been used since the encounter began
     */
    public boolean specialAbilityUsed() {
        return specialAbilityUsed.get();
    }

    /**
     * Marks the special ability as used for the current combat encounter.
     * Must only be called from the tick thread.
     */
    public void markSpecialAbilityUsed() {
        specialAbilityUsed.set(true);
    }

    /**
     * Forces this mob to prioritise attacking {@code taunter} for the next {@code durationTicks} AI
     * decisions, overriding its normal random-candidate targeting (see the Warrior TAUNT skill). A
     * fresh taunt fully replaces any previous one. Must only be called from the tick thread via
     * {@link MobRegistry}.
     *
     * @param taunter       the player drawing the mob's aggro
     * @param durationTicks how many AI decisions the taunt holds; must be positive
     */
    public void applyTaunt(Username taunter, int durationTicks) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("Taunt duration must be positive");
        }
        this.taunter.set(Objects.requireNonNull(taunter, "Taunter is required"));
        this.tauntTicksRemaining.set(durationTicks);
    }

    /**
     * Returns the player currently holding this mob's aggro via an active taunt, or {@code null} when
     * no taunt is active (never applied, expired, or cleared on disengage/respawn).
     *
     * @return the active taunter's username, or {@code null}
     */
    @Nullable
    public Username activeTaunter() {
        return tauntTicksRemaining.get() > 0 ? taunter.get() : null;
    }

    /**
     * Consumes one AI decision from an active taunt, expiring it (and clearing the taunter) when the
     * remaining count reaches zero. A no-op when no taunt is active. Must only be called from the tick
     * thread.
     */
    public void consumeTauntTick() {
        if (tauntTicksRemaining.get() > 0 && tauntTicksRemaining.decrementAndGet() <= 0) {
            clearTaunt();
        }
    }

    private void clearTaunt() {
        taunter.set(null);
        tauntTicksRemaining.set(0);
    }

    /**
     * Returns whether this mob is a temporary summoned pet (as opposed to an ordinary world mob).
     *
     * @return {@code true} when this instance was created via {@link #summoned}
     */
    public boolean isSummoned() {
        return summoner != null;
    }

    /**
     * Returns the player who summoned this pet, or {@code null} for ordinary world mobs.
     *
     * @return the summoner's username, or {@code null}
     */
    @Nullable
    public Username summoner() {
        return summoner;
    }

    /**
     * Returns whether this mob is a permanently tamed pet (see the TAME command).
     *
     * @return {@code true} when this instance was created via {@link #tamed}
     */
    public boolean isTamed() {
        return owner != null;
    }

    /**
     * Returns the player who permanently tamed this pet, or {@code null} for ordinary mobs and
     * temporary summons.
     *
     * @return the owner's username, or {@code null}
     */
    @Nullable
    public Username owner() {
        return owner;
    }

    /**
     * Returns the name to show for this mob: a tamed companion's owner-assigned custom name when one
     * is set (see the NAME command), otherwise the template name. Safe to call from any thread. Use
     * this everywhere a mob's identity is shown to players so named companions read as their custom
     * name in listings, room descriptions, and combat messages.
     *
     * @return the display name, never {@code null}
     */
    public String displayName() {
        String name = customName;
        return name != null ? name : template.name();
    }

    /**
     * Returns this tamed companion's custom display name, or {@code null} when it is unnamed.
     *
     * @return the custom name, or {@code null}
     */
    @Nullable
    public String customName() {
        return customName;
    }

    /**
     * Assigns a custom display name to this tamed companion (see the NAME command). Must only be
     * called from the tick thread.
     *
     * @param customName the new custom name; blank/{@code null} clears it back to the template name
     */
    public void setCustomName(@Nullable String customName) {
        this.customName = customName == null || customName.isBlank() ? null : customName;
    }

    /**
     * Returns this tamed companion's custom roleplay description, or {@code null} when it has none.
     * Safe to call from any thread.
     *
     * @return the custom description, or {@code null}
     */
    @Nullable
    public String customDescription() {
        return customDescription;
    }

    /**
     * Assigns a custom roleplay description to this tamed companion (see the DESCRIBE command). Once
     * set, this replaces the generic line shown when a player LOOKs at the companion. Must only be
     * called from the tick thread.
     *
     * @param customDescription the new custom description; blank/{@code null} clears it back to the
     *                          generic LOOK line
     */
    public void setDescription(@Nullable String customDescription) {
        this.customDescription =
            customDescription == null || customDescription.isBlank() ? null : customDescription;
    }

    /**
     * Returns whether this mob is a player-controlled pet — either a temporary {@link #isSummoned()
     * summon} or a permanently {@link #isTamed() tamed} companion. Pets never attack players, never
     * respawn as world mobs, and fight hostile mobs on their master's behalf.
     *
     * @return {@code true} when this instance has a summoner or an owner
     */
    public boolean isPet() {
        return summoner != null || owner != null;
    }

    /**
     * Returns the player this pet belongs to — its summoner if summoned, otherwise its owner if
     * tamed, or {@code null} for ordinary world mobs.
     *
     * @return the controlling player's username, or {@code null}
     */
    @Nullable
    public Username petOwner() {
        return summoner != null ? summoner : owner;
    }

    /** Returns the pet's remaining lifetime in ticks (zero for non-summoned mobs). */
    public int summonTicksRemaining() {
        return summonTicksRemaining.get();
    }

    /**
     * Decrements a summoned pet's remaining lifetime by one tick. Must only be called from the tick
     * thread and only for summoned pets.
     *
     * @return {@code true} when the lifetime has elapsed and the pet should be dismissed
     */
    public boolean tickSummonLifetime() {
        return summonTicksRemaining.decrementAndGet() <= 0;
    }

    /** Resets the mob to full HP and returns it to its spawn room, ready to act again. */
    public void respawn() {
        hp.set(template.maxHp());
        respawnTicksRemaining.set(0);
        engagedPlayers.clear();
        specialAbilityUsed.set(false);
        clearTaunt();
        currentRoomId = template.spawnRoomId();
    }
}
