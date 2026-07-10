package io.taanielo.jmud.core.gathering;

import java.util.Objects;

import io.taanielo.jmud.core.tick.Tickable;

/**
 * Per-tick service that advances resource-node respawn countdowns.
 *
 * <p>On each tick it asks {@link ResourceGatheringService} to decrement every depleted node's
 * remaining respawn ticks, making harvested nodes available again once their delay elapses. Respawn
 * timing is driven entirely by tick counts (no wall-clock timers), mirroring {@code CorpseDecayTicker}
 * and the mob respawn cycle (AGENTS.md §5).
 */
public class ResourceNodeRespawnTicker implements Tickable {

    private final ResourceGatheringService gatheringService;

    /**
     * Creates a respawn ticker over the given gathering service.
     *
     * @param gatheringService the service whose depletion countdowns are advanced each tick
     */
    public ResourceNodeRespawnTicker(ResourceGatheringService gatheringService) {
        this.gatheringService = Objects.requireNonNull(gatheringService, "Gathering service is required");
    }

    /**
     * Advances all depleted-node respawn countdowns by one tick.
     */
    @Override
    public void tick() {
        gatheringService.tickRespawns();
    }
}
