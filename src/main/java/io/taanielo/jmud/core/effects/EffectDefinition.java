package io.taanielo.jmud.core.effects;

import java.util.List;
import java.util.Objects;

public class EffectDefinition {
    private final EffectId id;
    private final String name;
    private final int durationTicks;
    private final int tickInterval;
    private final EffectStacking stacking;
    private final List<EffectModifier> modifiers;
    private final EffectMessages messages;

    public EffectDefinition(
        EffectId id,
        String name,
        int durationTicks,
        int tickInterval,
        EffectStacking stacking,
        List<EffectModifier> modifiers,
        EffectMessages messages
    ) {
        this.id = Objects.requireNonNull(id, "Effect id is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Effect name must not be blank");
        }
        if (durationTicks < 0) {
            throw new IllegalArgumentException("Effect duration must be non-negative");
        }
        if (tickInterval < 1) {
            throw new IllegalArgumentException("Tick interval must be at least 1");
        }
        this.name = name;
        this.durationTicks = durationTicks;
        this.tickInterval = tickInterval;
        this.stacking = Objects.requireNonNull(stacking, "Stacking is required");
        this.modifiers = List.copyOf(Objects.requireNonNullElse(modifiers, List.of()));
        this.messages = messages;
    }

    public EffectId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public int tickInterval() {
        return tickInterval;
    }

    public EffectStacking stacking() {
        return stacking;
    }

    public List<EffectModifier> modifiers() {
        return modifiers;
    }

    public EffectMessages messages() {
        return messages;
    }

    public boolean isPermanent() {
        return durationTicks == 0;
    }
}
