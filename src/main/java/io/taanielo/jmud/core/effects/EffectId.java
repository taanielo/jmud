package io.taanielo.jmud.core.effects;

import lombok.Value;

@Value
public class EffectId {
    String value;

    public static final EffectId HUNGER = new EffectId("hunger");
    public static final EffectId THIRST = new EffectId("thirst");

    public EffectId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Effect id must not be blank");
        }
        this.value = value;
    }

    public static EffectId of(String value) {
        return new EffectId(value);
    }
}
