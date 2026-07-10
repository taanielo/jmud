package io.taanielo.jmud.core.gathering;

import java.util.Locale;
import java.util.Objects;

/**
 * Value object wrapping a resource-node identifier (e.g. {@code sewers-iron-vein}).
 *
 * @param value the non-blank node id, normalised to lower case
 */
public record ResourceNodeId(String value) {

    public ResourceNodeId {
        Objects.requireNonNull(value, "Resource node id is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Resource node id must not be blank");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Creates a resource-node id from the given raw string.
     *
     * @param value the node id value
     * @return the node id value object
     */
    public static ResourceNodeId of(String value) {
        return new ResourceNodeId(value);
    }
}
