package io.taanielo.jmud.core.mob;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Value;

@Value
public class MobId {
    String value;

    public MobId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Mob id must not be blank");
        }
        this.value = value;
    }

    @JsonCreator
    public static MobId of(String value) {
        return new MobId(value);
    }

    @JsonValue
    public String jsonValue() {
        return value;
    }
}
