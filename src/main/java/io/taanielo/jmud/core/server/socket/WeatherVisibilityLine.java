package io.taanielo.jmud.core.server.socket;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.weather.Weather;
import io.taanielo.jmud.core.weather.WeatherEngine;
import io.taanielo.jmud.core.world.RoomService;

/**
 * Resolves the optional visibility status line shown in {@code WHO} and {@code SCORE} when the
 * player's current room is affected by weather that reduces visibility. Keeps the small lookup
 * (player location → room weather → visibility status) in one place so both commands stay in sync.
 */
final class WeatherVisibilityLine {

    private WeatherVisibilityLine() {
    }

    /**
     * Returns the visibility status line for the player's current room, or empty when the weather
     * subsystem is disabled, the player's location is unknown, or the weather does not affect
     * visibility.
     *
     * @param weatherEngine the weather source, or {@code null} when weather is disabled
     * @param roomService   service used to resolve the player's current room
     * @param player        the player whose surroundings are inspected
     * @return the visibility status line, if any
     */
    static Optional<String> forPlayer(
        @Nullable WeatherEngine weatherEngine, RoomService roomService, Player player) {
        if (weatherEngine == null) {
            return Optional.empty();
        }
        Weather weather = roomService.findPlayerLocation(player.getUsername())
            .map(weatherEngine::getWeatherAt)
            .orElseGet(Weather::clear);
        return weather.visibilityStatus();
    }
}
