package io.taanielo.jmud.core.character;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Value;

@Value
public class RaceId {
    String value;

    public RaceId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Race id must not be blank");
        }
        this.value = value;
    }

    @JsonCreator
    public static RaceId of(String value) {
        return new RaceId(value);
    }

    @JsonValue
    public String jsonValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
