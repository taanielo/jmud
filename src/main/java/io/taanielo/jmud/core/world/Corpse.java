package io.taanielo.jmud.core.world;

import java.time.Instant;
import java.util.Objects;

/**
 * Lightweight value object tracking a player corpse placed in the world.
 *
 * <p>Instances are transient — not persisted to disk. A {@link CorpseDecayTicker}
 * uses these records to remove expired corpse items from the room after the
 * configured decay period.
 *
 * @param itemId    the unique id of the corpse item placed in the room
 * @param roomId    the room where the corpse was spawned
 * @param ownerName the name of the player who died
 * @param gold      the amount of gold the player carried at time of death
 * @param spawnedAt the wall-clock instant when the corpse was created
 */
public record Corpse(ItemId itemId, RoomId roomId, String ownerName, int gold, Instant spawnedAt) {

    public Corpse {
        Objects.requireNonNull(itemId, "Item id is required");
        Objects.requireNonNull(roomId, "Room id is required");
        Objects.requireNonNull(ownerName, "Owner name is required");
        if (gold < 0) {
            throw new IllegalArgumentException("Gold must be non-negative");
        }
        Objects.requireNonNull(spawnedAt, "Spawned-at instant is required");
    }
}
