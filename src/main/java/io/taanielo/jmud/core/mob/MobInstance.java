package io.taanielo.jmud.core.mob;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.RoomId;

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

    public MobInstance(MobTemplate template) {
        this.template = template;
        this.hp = new AtomicInteger(template.maxHp());
        this.currentRoomId = template.spawnRoomId();
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

    /** Called once when the mob dies — starts the respawn countdown. */
    public void scheduleRespawn() {
        respawnTicksRemaining.set(template.respawnTicks());
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

    /** Resets the mob to full HP and returns it to its spawn room, ready to act again. */
    public void respawn() {
        hp.set(template.maxHp());
        respawnTicksRemaining.set(0);
        engagedPlayers.clear();
        specialAbilityUsed.set(false);
        currentRoomId = template.spawnRoomId();
    }
}
