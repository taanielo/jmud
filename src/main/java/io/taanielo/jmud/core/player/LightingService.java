package io.taanielo.jmud.core.player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.taanielo.jmud.core.world.Item;
import io.taanielo.jmud.core.world.Room;

/**
 * Domain service that resolves whether a player can see a room based on the room's ambient light
 * level and the light sources the player carries.
 *
 * <p>Visibility is computed at read time (on the tick thread, from LOOK and room-entry callbacks);
 * nothing is stored on the room or player, so there is no stale lighting state to keep in sync. A
 * naturally lit room is always visible; a dark room is only visible when the player carries an item
 * whose {@link Item#getLightRadius() light radius} meets or exceeds the room's
 * {@link Room#requiredLightRadius() required radius}. Only carried inventory items count — light
 * sources lying on the floor do not, encouraging players to pick them up.
 *
 * <p>This service is stateless and therefore safe to share across sessions.
 */
public class LightingService {

    /**
     * Message shown to a player who is in a dark room without a sufficient light source, in place
     * of the full room description.
     */
    public static final String DARKNESS_MESSAGE = "It is pitch dark. You cannot see your surroundings.";

    /**
     * Returns whether the given player can see the full contents of the given room.
     *
     * <p>Naturally lit rooms are always visible. Dark rooms are visible only when the player carries
     * a light source bright enough for the room's {@link Room#requiredLightRadius()}.
     *
     * @param player the player attempting to see the room
     * @param room   the room being observed
     * @return {@code true} if the player can see the room's full contents; {@code false} if the room
     *     is dark and the player lacks a sufficient light source
     */
    public boolean canSeeRoom(Player player, Room room) {
        Objects.requireNonNull(player, "Player is required");
        Objects.requireNonNull(room, "Room is required");
        if (!room.requiresLight()) {
            return true;
        }
        return carriedLightRadius(player) >= room.requiredLightRadius();
    }

    /**
     * Returns the brightest light radius among the player's carried inventory items, or {@code 0}
     * when the player carries no light source.
     *
     * @param player the player whose inventory is scanned
     * @return the maximum carried light radius, or {@code 0} if none
     */
    public int carriedLightRadius(Player player) {
        Objects.requireNonNull(player, "Player is required");
        return brightestLightSource(player)
            .map(Item::getLightRadius)
            .map(Integer::intValue)
            .orElse(0);
    }

    /**
     * Returns the brightest light-emitting item the player currently carries, if any.
     *
     * @param player the player whose inventory is scanned
     * @return the carried light source with the largest radius, or {@link Optional#empty()} when the
     *     player carries none
     */
    public Optional<Item> brightestLightSource(Player player) {
        Objects.requireNonNull(player, "Player is required");
        return player.getInventory().stream()
            .filter(Item::isLightSource)
            .max((left, right) -> Integer.compare(radiusOf(left), radiusOf(right)));
    }

    /**
     * Returns the abbreviated description lines shown to a player who cannot see the room.
     *
     * @return the single-line darkness description
     */
    public List<String> darknessLines() {
        return List.of(DARKNESS_MESSAGE);
    }

    private static int radiusOf(Item item) {
        Integer radius = item.getLightRadius();
        return radius == null ? 0 : radius;
    }
}
