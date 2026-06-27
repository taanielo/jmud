package io.taanielo.jmud.core.world;

import java.time.Duration;
import java.util.Objects;

import io.taanielo.jmud.core.tick.Tickable;

/**
 * Per-tick service that removes expired player corpses from the world.
 *
 * <p>On each tick, any corpse tracked by {@link RoomService} that is older than
 * the configured {@code decayAfter} duration is removed from its room.
 */
public class CorpseDecayTicker implements Tickable {

    private final RoomService roomService;
    private final Duration decayAfter;

    /**
     * Creates a corpse decay ticker.
     *
     * @param roomService the room service used to remove decayed corpse items
     * @param decayAfter  how long after creation a corpse persists before decaying;
     *                    must be positive
     */
    public CorpseDecayTicker(RoomService roomService, Duration decayAfter) {
        this.roomService = Objects.requireNonNull(roomService, "Room service is required");
        this.decayAfter = Objects.requireNonNull(decayAfter, "Decay duration is required");
        if (decayAfter.isNegative() || decayAfter.isZero()) {
            throw new IllegalArgumentException("Decay duration must be positive");
        }
    }

    /**
     * Removes all corpses whose age exceeds the configured decay duration.
     */
    @Override
    public void tick() {
        roomService.removeExpiredCorpses(decayAfter);
    }
}
