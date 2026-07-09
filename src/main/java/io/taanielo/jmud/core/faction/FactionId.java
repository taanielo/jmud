package io.taanielo.jmud.core.faction;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Value object wrapping a faction identifier string (e.g. {@code bandits}).
 *
 * <p>A mob template links to a faction by this id via its {@code faction_id} field, and a player's
 * {@link PlayerReputation} keys standing values by it.
 */
public record FactionId(String value) {

    public FactionId {
        Objects.requireNonNull(value, "Faction id value is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Faction id must not be blank");
        }
    }

    @JsonCreator
    public static FactionId of(String value) {
        return new FactionId(value);
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
