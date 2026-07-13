package io.taanielo.jmud.core.mob;

import java.util.List;
import java.util.Optional;

import io.taanielo.jmud.core.world.RoomId;

/**
 * Narrow view of {@link MobRegistry} used by the {@link WorldEventScheduler} to place and remove the
 * rare-elite mob backing a timed world event, without coupling the scheduler to the whole combat
 * registry.
 *
 * <p>Every method mutates or reads live mob state and therefore must only be called on the tick
 * thread (AGENTS.md §5). {@link MobRegistry} is the sole production implementation.
 */
public interface WorldEventStage {

    /**
     * Returns the mob templates flagged {@link MobTemplate#worldEvent()}, i.e. the pool of
     * rare-elite encounters the scheduler may open a world event with. The returned list reflects the
     * templates cached at registry initialisation, so no disk I/O occurs on the tick thread.
     *
     * @return the world-event templates (never null, may be empty when no world-event mob is defined)
     */
    List<MobTemplate> worldEventTemplates();

    /**
     * Spawns a fresh instance of the given template into the given room, registering it so it takes
     * part in AI and combat from the next tick.
     *
     * @param mobId  the template id to instantiate
     * @param roomId the room to place the new instance in
     * @return the spawned instance, or empty when no template with that id exists
     */
    Optional<MobInstance> spawnInstance(MobId mobId, RoomId roomId);

    /**
     * Removes the given live mob instance outright with no respawn scheduled, tearing down any player
     * combat engagement against it. Used to clear a world-event mob when its window closes.
     *
     * @param mob the instance to remove
     */
    void purgeInstance(MobInstance mob);
}
