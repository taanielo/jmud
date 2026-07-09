package io.taanielo.jmud.core.weather;

import java.util.Objects;

import io.taanielo.jmud.core.combat.CombatRandom;
import io.taanielo.jmud.core.tick.Tickable;
import io.taanielo.jmud.core.world.Room;
import io.taanielo.jmud.core.world.RoomId;
import io.taanielo.jmud.core.world.repository.RepositoryException;
import io.taanielo.jmud.core.world.repository.RoomRepository;

/**
 * Tick-driven source of the world's current {@link Weather}.
 *
 * <p>A single global weather snapshot evolves on the tick thread (AGENTS.md §5): every tick the
 * engine steps the current intensity toward a target, and periodically re-rolls the target type and
 * intensity through the {@link CombatRandom} port so the sequence is deterministic and replayable.
 * Transitions are smooth — to change type the intensity first ramps down to zero, the type swaps,
 * then it ramps back up, so weather never snaps between states.
 *
 * <p>The current snapshot is held in a {@code volatile} field (the {@link Weather} record is
 * immutable, so publishing a fresh reference is safe) and can be read as a point-in-time value from
 * any thread; the transition bookkeeping fields are only ever touched from {@link #tick()} on the
 * tick thread. Weather applies only to rooms flagged {@link Room#isOutdoor() outdoor}; indoor rooms
 * always report {@link Weather#clear()}.
 */
public class WeatherEngine implements Tickable {

    private static final int CLEAR_WEIGHT = 40;
    private static final int RAIN_WEIGHT = 20;
    private static final int FOG_WEIGHT = 20;
    private static final int MIN_TARGET_INTENSITY = 30;
    private static final int MAX_TARGET_INTENSITY = 100;

    private final CombatRandom random;
    private final RoomRepository roomRepository;
    private final int transitionIntervalTicks;
    private final int intensityStep;
    private volatile Weather currentWeather = Weather.clear();

    // Tick-thread-only transition bookkeeping.
    private WeatherType targetType = WeatherType.CLEAR;
    private int targetIntensity;
    private long tickCount;

    /**
     * Creates a weather engine.
     *
     * @param random                  the RNG port used to pick weather transitions (no bare
     *                                {@code Random}; AGENTS.md §5)
     * @param roomRepository          repository consulted to determine whether a room is outdoor
     * @param transitionIntervalTicks number of ticks between target re-rolls; must be positive
     * @param intensityStep           per-tick intensity change (0-100 scale); must be positive
     */
    public WeatherEngine(
        CombatRandom random,
        RoomRepository roomRepository,
        int transitionIntervalTicks,
        int intensityStep
    ) {
        this.random = Objects.requireNonNull(random, "Combat random is required");
        this.roomRepository = Objects.requireNonNull(roomRepository, "Room repository is required");
        if (transitionIntervalTicks <= 0) {
            throw new IllegalArgumentException("Transition interval must be positive");
        }
        if (intensityStep <= 0) {
            throw new IllegalArgumentException("Intensity step must be positive");
        }
        this.transitionIntervalTicks = transitionIntervalTicks;
        this.intensityStep = intensityStep;
    }

    /**
     * Advances the weather by one tick. Must only be called from the tick thread.
     */
    @Override
    public void tick() {
        tickCount++;
        if (tickCount % transitionIntervalTicks == 0) {
            rollTarget();
        }
        Weather weather = currentWeather;
        WeatherType type = weather.type();
        int intensity = weather.intensity();
        if (type != targetType) {
            intensity -= intensityStep;
            if (intensity <= 0) {
                intensity = 0;
                type = targetType;
            }
        } else if (intensity < targetIntensity) {
            intensity = Math.min(targetIntensity, intensity + intensityStep);
        } else if (intensity > targetIntensity) {
            intensity = Math.max(targetIntensity, intensity - intensityStep);
        }
        currentWeather = new Weather(type, intensity);
    }

    private void rollTarget() {
        int roll = random.roll(1, 100);
        if (roll <= CLEAR_WEIGHT) {
            targetType = WeatherType.CLEAR;
        } else if (roll <= CLEAR_WEIGHT + RAIN_WEIGHT) {
            targetType = WeatherType.RAIN;
        } else if (roll <= CLEAR_WEIGHT + RAIN_WEIGHT + FOG_WEIGHT) {
            targetType = WeatherType.FOG;
        } else {
            targetType = WeatherType.STORM;
        }
        targetIntensity = targetType == WeatherType.CLEAR
            ? 0
            : random.roll(MIN_TARGET_INTENSITY, MAX_TARGET_INTENSITY);
    }

    /**
     * Returns the current global weather snapshot. Safe to read from any thread.
     */
    public Weather current() {
        return currentWeather;
    }

    /**
     * Returns the weather affecting the given room: the current global weather when the room is
     * outdoor, otherwise {@link Weather#clear()} (indoor rooms are always shielded).
     *
     * @param room the room to evaluate
     * @return the weather in effect for that room
     */
    public Weather weatherFor(Room room) {
        Objects.requireNonNull(room, "Room is required");
        return room.isOutdoor() ? currentWeather : Weather.clear();
    }

    /**
     * Returns the weather affecting the room with the given id, resolving the room through the
     * repository. Returns {@link Weather#clear()} when the room is unknown, indoor, or cannot be
     * loaded.
     *
     * @param roomId the id of the room to evaluate
     * @return the weather in effect for that room
     */
    public Weather getWeatherAt(RoomId roomId) {
        Objects.requireNonNull(roomId, "Room id is required");
        try {
            return roomRepository.findById(roomId)
                .map(this::weatherFor)
                .orElse(Weather.clear());
        } catch (RepositoryException e) {
            return Weather.clear();
        }
    }
}
