package io.taanielo.jmud.core.action;

import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.world.RoomId;

/**
 * Port that lets {@link GameActionService} discover which mobs occupy a given room without
 * depending on the concrete mob layer.
 *
 * <p>The mob layer ({@code core.mob.MobRegistry}) already depends on {@code core.action}, so
 * defining this port here and having the mob layer supply it keeps the dependency direction
 * one-way (mob → action). The ranger TRACK skill uses it to walk the room graph (via
 * {@link io.taanielo.jmud.core.world.RoomService#getExits}) and locate the nearest mob of a
 * named type; the {@code QUEST TRACK} guidance command uses the same walk but matches on the
 * mob's template id (the quest's {@code targetMobId}) rather than its display name. All reads
 * happen on the tick thread via the player command queue (AGENTS.md §5); the returned list is a
 * point-in-time snapshot and is never mutated by the caller.
 */
public interface MobLocatorPort {

    /**
     * A no-op port that reports no mobs in any room, used as the default when no mob layer is
     * wired (e.g. in unit tests of unrelated actions).
     */
    MobLocatorPort NONE = roomId -> List.of();

    /**
     * Returns the live mobs currently in the given room, each carrying its template id and its
     * display name.
     *
     * @param roomId the room to inspect
     * @return the live mobs in the room; never null, possibly empty
     */
    List<TrackableMob> liveMobsInRoom(RoomId roomId);

    /**
     * Returns the display names of all live mobs currently in the given room.
     *
     * @param roomId the room to inspect
     * @return the display names of the live mobs in the room (e.g. {@code "Goblin"}); never null,
     *         possibly empty
     */
    default List<String> liveMobNamesInRoom(RoomId roomId) {
        return liveMobsInRoom(roomId).stream().map(TrackableMob::displayName).toList();
    }

    /**
     * A live mob discovered while walking the room graph, identified both by its template id (used
     * to match a kill quest's {@code targetMobId}) and its display name (used in TRACK output).
     *
     * @param templateId  the mob template id (e.g. {@code "goblin"}); must not be null
     * @param displayName the mob's display name (e.g. {@code "Goblin"}); must not be null
     */
    record TrackableMob(String templateId, String displayName) {
        public TrackableMob {
            Objects.requireNonNull(templateId, "templateId is required");
            Objects.requireNonNull(displayName, "displayName is required");
        }
    }
}
