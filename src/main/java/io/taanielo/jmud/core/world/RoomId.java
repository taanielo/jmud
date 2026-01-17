package io.taanielo.jmud.core.world;

import lombok.Value;

@Value
public class RoomId {
    String value;

    public RoomId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Room id must not be blank");
        }
        this.value = value;
    }

    public static RoomId of(String value) {
        return new RoomId(value);
    }
}
