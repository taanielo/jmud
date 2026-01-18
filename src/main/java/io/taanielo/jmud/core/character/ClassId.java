package io.taanielo.jmud.core.character;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Value;

@Value
public class ClassId {
    String value;

    public ClassId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Class id must not be blank");
        }
        this.value = value;
    }

    @JsonCreator
    public static ClassId of(String value) {
        return new ClassId(value);
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
