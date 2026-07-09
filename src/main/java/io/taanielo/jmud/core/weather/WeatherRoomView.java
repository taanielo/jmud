package io.taanielo.jmud.core.weather;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomWeatherView;

/**
 * Adapts {@link WeatherEngine} to the world-layer {@link RoomWeatherView} port so that
 * {@link io.taanielo.jmud.core.world.RoomService} can append weather lines to outdoor room
 * descriptions without depending on the weather subsystem (the dependency points weather → world).
 */
public final class WeatherRoomView implements RoomWeatherView {

    private final WeatherEngine weatherEngine;

    /**
     * Creates a view backed by the given engine.
     *
     * @param weatherEngine the tick-driven weather source
     */
    public WeatherRoomView(WeatherEngine weatherEngine) {
        this.weatherEngine = Objects.requireNonNull(weatherEngine, "Weather engine is required");
    }

    @Override
    public List<String> weatherLines(Room room) {
        Objects.requireNonNull(room, "Room is required");
        Weather weather = weatherEngine.weatherFor(room);
        List<String> lines = new ArrayList<>();
        weather.roomDescription().ifPresent(lines::add);
        weather.visibilityStatus().ifPresent(lines::add);
        return lines;
    }
}
