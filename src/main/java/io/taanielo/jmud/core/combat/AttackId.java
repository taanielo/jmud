package io.taanielo.jmud.core.combat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Value;

@Value
public class AttackId {
    String value;

    public AttackId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Attack id must not be blank");
        }
        this.value = value;
    }

    @JsonCreator
    public static AttackId of(String value) {
        return new AttackId(value);
    }

    @JsonValue
    public String jsonValue() {
        return value;
    }
}
