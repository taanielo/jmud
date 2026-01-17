package io.taanielo.jmud.core.world;

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

    public static ItemId of(String value) {
        return new ItemId(value);
    }
}
