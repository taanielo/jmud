package io.taanielo.jmud.core.mob;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.taanielo.jmud.core.world.RoomId;

/**
 * A live mob instance in the world.
 *
 * <p>HP is tracked via {@link AtomicInteger} so that player-thread attacks
 * (command queue) and tick-thread AI reads are both safe. All other state
 * mutations are confined to the tick thread.
 */
public class MobInstance {

    private final UUID instanceId = UUID.randomUUID();
    private final MobTemplate template;
    private final AtomicInteger hp;
    private final AtomicInteger respawnTicksRemaining = new AtomicInteger(0);

    public MobInstance(MobTemplate template) {
        this.template = template;
        this.hp = new AtomicInteger(template.maxHp());
    }

    public UUID instanceId() {
        return instanceId;
    }

    public MobTemplate template() {
        return template;
    }

    public RoomId roomId() {
        return template.spawnRoomId();
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

    /** Resets the mob to full HP, ready to act again. */
    public void respawn() {
        hp.set(template.maxHp());
        respawnTicksRemaining.set(0);
    }
}
