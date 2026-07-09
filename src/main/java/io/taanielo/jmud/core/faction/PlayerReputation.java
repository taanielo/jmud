package io.taanielo.jmud.core.faction;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * A player's reputation standing with each faction they have interacted with.
 *
 * <p>Standing is a signed integer: positive means friendly, negative means hostile, and an untracked
 * faction is treated as neutral ({@code 0}). The map is immutable; mutating operations return a new
 * instance, so it is safe to hand between the tick thread and the write-behind persistence queue.
 *
 * @param standings immutable map of faction id to signed standing value
 */
public record PlayerReputation(Map<FactionId, Integer> standings) {

    public PlayerReputation {
        standings = standings == null ? Map.of() : Map.copyOf(standings);
    }

    /** Returns an empty reputation with no tracked factions. */
    public static PlayerReputation empty() {
        return new PlayerReputation(Map.of());
    }

    /**
     * Rebuilds a {@link PlayerReputation} from its persisted string-keyed form.
     *
     * @param raw the persisted map of faction-id string to standing, or {@code null}
     * @return the reconstructed reputation (empty when {@code raw} is null or empty)
     */
    public static PlayerReputation fromStringMap(@Nullable Map<String, Integer> raw) {
        if (raw == null || raw.isEmpty()) {
            return empty();
        }
        Map<FactionId, Integer> converted = new HashMap<>();
        for (Map.Entry<String, Integer> entry : raw.entrySet()) {
            Integer value = entry.getValue();
            if (value != null) {
                converted.put(FactionId.of(entry.getKey()), value);
            }
        }
        return new PlayerReputation(converted);
    }

    /**
     * Returns the player's standing with the given faction, or {@code 0} (neutral) when the faction
     * has never been encountered.
     *
     * @param factionId the faction to query
     * @return the signed standing value
     */
    public int standing(FactionId factionId) {
        return standings.getOrDefault(factionId, 0);
    }

    /**
     * Returns a copy of this reputation with the given delta added to the standing of the given
     * faction. A {@code delta} of {@code 0} returns this instance unchanged.
     *
     * @param factionId the faction whose standing changes
     * @param delta     the signed amount to add
     * @return the updated reputation
     */
    public PlayerReputation adjust(FactionId factionId, int delta) {
        if (delta == 0) {
            return this;
        }
        Map<FactionId, Integer> next = new HashMap<>(standings);
        next.merge(factionId, delta, Integer::sum);
        return new PlayerReputation(next);
    }

    /** Returns {@code true} when no faction standings are tracked. */
    public boolean isEmpty() {
        return standings.isEmpty();
    }

    /**
     * Returns the persisted string-keyed form of this reputation, suitable for JSON serialisation on
     * the player record. The iteration order is stable to keep save files diff-friendly.
     *
     * @return an ordered map of faction-id string to standing
     */
    public Map<String, Integer> toStringMap() {
        Map<String, Integer> raw = new LinkedHashMap<>();
        standings.forEach((id, value) -> raw.put(id.getValue(), value));
        return raw;
    }
}
