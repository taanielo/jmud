package io.taanielo.jmud.core.ability;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Value;

@Value
public class AbilityId {
    String value;

    public AbilityId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Ability id must not be blank");
        }
        this.value = value;
    }

    @JsonCreator
    public static AbilityId of(String value) {
        return new AbilityId(value);
    }

    @JsonValue
    public String jsonValue() {
        return value;
    }
}
