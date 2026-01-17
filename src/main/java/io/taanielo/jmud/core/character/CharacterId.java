package io.taanielo.jmud.core.character;

import lombok.Value;

@Value
public class CharacterId {
    String value;

    public CharacterId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Character id must not be blank");
        }
        this.value = value;
    }

    public static CharacterId of(String value) {
        return new CharacterId(value);
    }
}
