package io.taanielo.jmud.core.effects;

import java.util.Objects;

public class EffectModifier {
    private final String stat;
    private final ModifierOperation operation;
    private final int amount;

    public EffectModifier(String stat, ModifierOperation operation, int amount) {
        if (stat == null || stat.isBlank()) {
            throw new IllegalArgumentException("Modifier stat must not be blank");
        }
        this.stat = stat;
        this.operation = Objects.requireNonNull(operation, "Modifier operation is required");
        this.amount = amount;
    }

    public String stat() {
        return stat;
    }

    public ModifierOperation operation() {
        return operation;
    }

    public int amount() {
        return amount;
    }
}
