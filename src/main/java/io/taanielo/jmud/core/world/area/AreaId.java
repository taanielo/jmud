package io.taanielo.jmud.core.world.area;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Value;

/**
 * Stable identifier of a world {@link Area} (e.g. {@code "frozen-peaks"}). Also used as the value of
 * an item's {@code map_area_id}, binding a map item to the area whose cartography it renders.
 *
 * <p>Serializes as a plain string (via {@link JsonValue}) so a map item carried in a player's saved
 * inventory round-trips its {@code map_area_id} as {@code "frozen-peaks"} rather than a nested
 * object.
 */
@Value
public class AreaId {
    String value;

    /**
     * Creates an area id.
     *
     * @param value the non-blank id string
     */
    public AreaId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Area id must not be blank");
        }
        this.value = value;
    }

    /**
     * Deserializes an area id from its plain-string JSON form.
     *
     * @param value the non-blank id string
     * @return the area id
     */
    @JsonCreator
    public static AreaId of(String value) {
        return new AreaId(value);
    }

    /**
     * Returns the plain-string JSON representation of this id.
     *
     * @return the id string
     */
    @JsonValue
    public String jsonValue() {
        return value;
    }
}
