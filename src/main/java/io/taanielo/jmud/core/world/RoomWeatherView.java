package io.taanielo.jmud.core.world;

import java.util.List;

/**
 * Port through which {@link RoomService} learns the current weather effect on a room without
 * depending on the weather subsystem directly.
 *
 * <p>Defined in the world package and implemented by an adapter in the weather package, so that the
 * dependency points weather → world (never the reverse). Returns the display lines (description and
 * visibility status) to append to an outdoor room's look output; an empty list means no weather is
 * currently affecting the room.
 */
public interface RoomWeatherView {

    /**
     * Returns the weather lines to append to the given room's look description, or an empty list
     * when no weather applies (e.g. the room is indoors or the sky is clear).
     *
     * @param room the room being described
     * @return the weather description/visibility lines, possibly empty
     */
    List<String> weatherLines(Room room);
}
