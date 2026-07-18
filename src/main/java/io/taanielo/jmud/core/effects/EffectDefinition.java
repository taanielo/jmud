package io.taanielo.jmud.core.effects;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.taanielo.jmud.core.messaging.MessageSpec;

public class EffectDefinition {
    private final EffectId id;
    private final String name;
    private final int durationTicks;
    private final int tickInterval;
    private final EffectStacking stacking;
    private final List<EffectModifier> modifiers;
    private final List<MessageSpec> messages;
    private final @Nullable ControlType control;

    public EffectDefinition(
        EffectId id,
        String name,
        int durationTicks,
        int tickInterval,
        EffectStacking stacking,
        List<EffectModifier> modifiers,
        List<MessageSpec> messages
    ) {
        this(id, name, durationTicks, tickInterval, stacking, modifiers, messages, null);
    }

    public EffectDefinition(
        EffectId id,
        String name,
        int durationTicks,
        int tickInterval,
        EffectStacking stacking,
        List<EffectModifier> modifiers,
        List<MessageSpec> messages,
        @Nullable ControlType control
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
        this.messages = List.copyOf(Objects.requireNonNullElse(messages, List.of()));
        this.control = control;
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

    public List<MessageSpec> messages() {
        return messages;
    }

    /**
     * Returns this effect's hard crowd-control classification, if any. An effect without a
     * classification (the default) only ever applies stat modifiers; a classified effect also
     * mechanically forbids the corresponding player actions (movement/flee for {@link ControlType#ROOT},
     * spellcasting for {@link ControlType#SILENCE}, everything for {@link ControlType#STUN}).
     *
     * @return the control classification, or empty when the effect imposes no hard control
     */
    public Optional<ControlType> control() {
        return Optional.ofNullable(control);
    }

    public boolean isPermanent() {
        return durationTicks == 0;
    }

    /**
     * Classifies this effect as harmful (a debuff or damage-over-time) versus beneficial
     * (a buff or heal-over-time), derived from the net direction of its stat modifiers so
     * that player-facing listings such as the {@code EFFECTS} command can visually separate
     * what is helping the bearer from what is hurting them.
     *
     * <p>A positive {@code damage_per_tick} counts against the bearer (it is a damage-over-time
     * effect); every other modifier contributes its signed amount as a benefit. The effect is
     * harmful when the summed benefit is negative. Effects whose modifiers net to zero (or that
     * carry none) are treated as not harmful.
     *
     * @return {@code true} when the effect's net modifier direction disadvantages its bearer
     */
    public boolean isHarmful() {
        int benefit = 0;
        for (EffectModifier modifier : modifiers) {
            if ("damage_per_tick".equalsIgnoreCase(modifier.stat().trim())) {
                benefit -= modifier.amount();
            } else {
                benefit += modifier.amount();
            }
        }
        return benefit < 0;
    }
}
