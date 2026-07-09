package io.taanielo.jmud.core.achievement;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * A player's unlocked achievements, keyed by {@link AchievementId} with the {@link Instant} the
 * achievement was unlocked.
 *
 * <p>The map is immutable; unlocking returns a new instance, so it is safe to hand between the tick
 * thread and the write-behind persistence queue (AGENTS.md §5). Re-unlocking an already unlocked
 * achievement is a no-op, preserving the original unlock time.
 *
 * @param unlocked immutable map of achievement id to unlock timestamp
 */
public record PlayerAchievements(Map<AchievementId, Instant> unlocked) {

    public PlayerAchievements {
        unlocked = unlocked == null ? Map.of() : Map.copyOf(unlocked);
    }

    /** Returns an empty component with no unlocked achievements. */
    public static PlayerAchievements empty() {
        return new PlayerAchievements(Map.of());
    }

    /**
     * Rebuilds a {@link PlayerAchievements} from its persisted string-keyed form (achievement-id
     * string to ISO-8601 unlock instant).
     *
     * @param raw the persisted map, or {@code null}
     * @return the reconstructed component (empty when {@code raw} is null or empty)
     */
    public static PlayerAchievements fromStringMap(@Nullable Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            return empty();
        }
        Map<AchievementId, Instant> converted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            String value = entry.getValue();
            if (value != null && !value.isBlank()) {
                converted.put(AchievementId.of(entry.getKey()), Instant.parse(value));
            }
        }
        return new PlayerAchievements(converted);
    }

    /**
     * Returns {@code true} when the given achievement has already been unlocked.
     *
     * @param id the achievement id to check; must not be null
     */
    public boolean has(AchievementId id) {
        Objects.requireNonNull(id, "id is required");
        return unlocked.containsKey(id);
    }

    /**
     * Returns the instant the given achievement was unlocked, if it has been unlocked.
     *
     * @param id the achievement id to query; must not be null
     */
    public Optional<Instant> unlockedAt(AchievementId id) {
        Objects.requireNonNull(id, "id is required");
        return Optional.ofNullable(unlocked.get(id));
    }

    /**
     * Returns a copy of this component with the given achievement unlocked at the given instant,
     * unless it was already unlocked, in which case this instance is returned unchanged.
     *
     * @param id the achievement id to unlock; must not be null
     * @param at the unlock timestamp; must not be null
     */
    public PlayerAchievements unlock(AchievementId id, Instant at) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(at, "at is required");
        if (unlocked.containsKey(id)) {
            return this;
        }
        Map<AchievementId, Instant> next = new LinkedHashMap<>(unlocked);
        next.put(id, at);
        return new PlayerAchievements(next);
    }

    /** Returns {@code true} when no achievements are unlocked. */
    public boolean isEmpty() {
        return unlocked.isEmpty();
    }

    /** Returns the number of unlocked achievements. */
    public int size() {
        return unlocked.size();
    }

    /**
     * Returns the persisted string-keyed form of this component, suitable for JSON serialisation on
     * the player record. The iteration order is stable to keep save files diff-friendly.
     *
     * @return an ordered map of achievement-id string to ISO-8601 unlock instant
     */
    public Map<String, String> toStringMap() {
        Map<String, String> raw = new LinkedHashMap<>();
        unlocked.forEach((id, at) -> raw.put(id.getValue(), at.toString()));
        return raw;
    }
}
