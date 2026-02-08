package io.taanielo.jmud.core.world;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Value;

@Value
public class ItemId {
    String value;

    public ItemId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Item id must not be blank");
        }
        this.value = value;
    }

    @JsonCreator
    public static ItemId fromJson(@JsonProperty("value") String value) {
        return new ItemId(value);
    }

    @JsonValue
    public String jsonValue() {
        return value;
    }

    public static ItemId of(String value) {
        return new ItemId(value);
    }
}
