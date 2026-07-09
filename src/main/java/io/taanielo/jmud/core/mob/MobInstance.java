package io.taanielo.jmud.core.mob;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    public MobInstance(MobTemplate template) {
        this.template = template;
        this.hp = new AtomicInteger(template.maxHp());
        this.currentRoomId = template.spawnRoomId();
        this.summoner = null;
    }

    private MobInstance(MobTemplate template, RoomId spawnRoom, Username summoner, int durationTicks) {
        this.template = Objects.requireNonNull(template, "Template is required");
        this.hp = new AtomicInteger(template.maxHp());
        this.currentRoomId = Objects.requireNonNull(spawnRoom, "Spawn room is required");
        this.summoner = Objects.requireNonNull(summoner, "Summoner is required");
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("Summon duration must be positive");
        }
        this.summonTicksRemaining.set(durationTicks);
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
        if (engagedPlayers.isEmpty()) {
            specialAbilityUsed.set(false);
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
        currentRoomId = template.spawnRoomId();
    }
}
