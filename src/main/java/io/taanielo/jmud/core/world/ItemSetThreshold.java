package io.taanielo.jmud.core.world;

import java.util.Map;
import java.util.Objects;

/**
 * A single {@code { pieces_required, stats }} threshold of an {@link ItemSet}. While the number of
 * worn set pieces reaches {@link #piecesRequired()}, this threshold's {@link #stats()} are added,
 * additively and on top of each piece's own attributes, to the wearer's effective stats. Multiple
 * met thresholds stack.
 *
 * @param piecesRequired number of worn set pieces at or above which this threshold's bonus applies;
 *                       must be at least 2
 * @param stats          the additive stat bonuses granted while this threshold is met; never empty
 */
public record ItemSetThreshold(int piecesRequired, Map<String, Integer> stats) {

    public ItemSetThreshold {
        if (piecesRequired < 2) {
            throw new IllegalArgumentException("Item set threshold pieces_required must be at least 2");
        }
        Objects.requireNonNull(stats, "Item set threshold stats are required");
        if (stats.isEmpty()) {
            throw new IllegalArgumentException("Item set threshold stats must not be empty");
        }
        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Item set threshold stat keys must not be blank");
            }
            Objects.requireNonNull(entry.getValue(), "Item set threshold stat values must not be null");
        }
        stats = Map.copyOf(stats);
    }
}
