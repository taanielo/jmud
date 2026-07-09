package io.taanielo.jmud.core.transport;

import java.util.Objects;

/**
 * Stable identifier for a {@link Ferry}, mirroring the {@code id} field of its JSON definition.
 *
 * @param value the non-blank ferry id
 */
public record FerryId(String value) {

    /**
     * Creates a ferry id, rejecting blank values.
     *
     * @param value the non-blank ferry id
     */
    public FerryId {
        Objects.requireNonNull(value, "Ferry id is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Ferry id must not be blank");
        }
    }

    /**
     * Convenience factory equivalent to the canonical constructor.
     *
     * @param value the non-blank ferry id
     * @return a ferry id wrapping {@code value}
     */
    public static FerryId of(String value) {
        return new FerryId(value);
    }
}
