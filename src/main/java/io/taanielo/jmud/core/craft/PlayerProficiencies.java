package io.taanielo.jmud.core.craft;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * A player's crafting proficiency in each profession they have practised.
 *
 * <p>Proficiency is tracked as accumulated <em>points</em> per {@link ProfessionId}; the visible
 * profession <em>level</em> is derived by integer division by {@link #POINTS_PER_LEVEL}. An untracked
 * profession is treated as level {@code 0} with {@code 0} points, so existing save files with no
 * proficiency data load with every profession at level 0 (no migration required). The backing map is
 * immutable; mutating operations return a new instance, so it is safe to hand between the tick thread
 * and the write-behind persistence queue.
 *
 * @param points immutable map of profession id to accumulated proficiency points
 */
public record PlayerProficiencies(Map<ProfessionId, Integer> points) {

    /** Points required to advance one profession level. */
    public static final int POINTS_PER_LEVEL = 100;

    public PlayerProficiencies {
        points = points == null ? Map.of() : Map.copyOf(points);
    }

    /** Returns an empty proficiency set with no practised professions. */
    public static PlayerProficiencies empty() {
        return new PlayerProficiencies(Map.of());
    }

    /**
     * Rebuilds a {@link PlayerProficiencies} from its persisted string-keyed form.
     *
     * @param raw the persisted map of profession-id string to accumulated points, or {@code null}
     * @return the reconstructed proficiencies (empty when {@code raw} is null or empty)
     */
    public static PlayerProficiencies fromStringMap(@Nullable Map<String, Integer> raw) {
        if (raw == null || raw.isEmpty()) {
            return empty();
        }
        Map<ProfessionId, Integer> converted = new HashMap<>();
        for (Map.Entry<String, Integer> entry : raw.entrySet()) {
            Integer value = entry.getValue();
            if (value != null && value > 0) {
                converted.put(ProfessionId.of(entry.getKey()), value);
            }
        }
        return new PlayerProficiencies(converted);
    }

    /**
     * Returns the accumulated proficiency points in the given profession, or {@code 0} when it has
     * never been practised.
     *
     * @param professionId the profession to query
     * @return the accumulated points, never negative
     */
    public int points(ProfessionId professionId) {
        return points.getOrDefault(professionId, 0);
    }

    /**
     * Returns the player's current level in the given profession, derived from accumulated points.
     *
     * @param professionId the profession to query
     * @return the profession level, {@code 0} when never practised
     */
    public int level(ProfessionId professionId) {
        return points(professionId) / POINTS_PER_LEVEL;
    }

    /**
     * Returns a copy of these proficiencies with the given points added to the named profession. A
     * non-positive {@code amount} returns this instance unchanged.
     *
     * @param professionId the profession whose proficiency grows
     * @param amount       the points to add; ignored when {@code <= 0}
     * @return the updated proficiencies
     */
    public PlayerProficiencies gain(ProfessionId professionId, int amount) {
        if (amount <= 0) {
            return this;
        }
        Map<ProfessionId, Integer> next = new HashMap<>(points);
        next.merge(professionId, amount, Integer::sum);
        return new PlayerProficiencies(next);
    }

    /** Returns {@code true} when no profession has been practised. */
    public boolean isEmpty() {
        return points.isEmpty();
    }

    /**
     * Returns the persisted string-keyed form of these proficiencies, suitable for JSON serialisation
     * on the player record. The iteration order is stable to keep save files diff-friendly.
     *
     * @return an ordered map of profession-id string to accumulated points
     */
    public Map<String, Integer> toStringMap() {
        Map<String, Integer> raw = new LinkedHashMap<>();
        points.forEach((id, value) -> raw.put(id.value(), value));
        return raw;
    }
}
