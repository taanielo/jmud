package io.taanielo.jmud.core.needs;

import lombok.Value;

@Value
public class NeedId {
    String value;

    public static final NeedId HUNGER = new NeedId("hunger");
    public static final NeedId THIRST = new NeedId("thirst");

    public NeedId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Need id must not be blank");
        }
        this.value = value;
    }

    public static NeedId of(String value) {
        return new NeedId(value);
    }
}
