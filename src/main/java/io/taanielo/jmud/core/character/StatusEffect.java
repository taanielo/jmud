package io.taanielo.jmud.core.character;

import lombok.Value;

@Value
public class StatusEffect {
    String name;

    public StatusEffect(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Status effect name must not be blank");
        }
        this.name = name;
    }

    public static StatusEffect of(String name) {
        return new StatusEffect(name);
    }
}
