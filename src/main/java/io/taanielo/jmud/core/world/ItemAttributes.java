package io.taanielo.jmud.core.world;

import java.util.Map;
import java.util.Objects;

import lombok.Value;

@Value
public class ItemAttributes {
    Map<String, Integer> stats;

    public ItemAttributes(Map<String, Integer> stats) {
        Objects.requireNonNull(stats, "Item stats are required");
        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Item stat keys must not be blank");
            }
            Objects.requireNonNull(entry.getValue(), "Item stat values must not be null");
        }
        this.stats = Map.copyOf(stats);
    }

    public static ItemAttributes empty() {
        return new ItemAttributes(Map.of());
    }
}
