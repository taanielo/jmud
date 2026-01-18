package io.taanielo.jmud.core.effects;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class EffectInstance {
    private final EffectId id;
    private int remainingTicks;
    private int stacks;

    @JsonCreator
    public EffectInstance(
        @JsonProperty("id") EffectId id,
        @JsonProperty("remainingTicks") int remainingTicks,
        @JsonProperty("stacks") int stacks
    ) {
        this.id = Objects.requireNonNull(id, "Effect id is required");
        if (remainingTicks < 0) {
            throw new IllegalArgumentException("Remaining ticks must be non-negative");
        }
        if (stacks < 1) {
            throw new IllegalArgumentException("Stacks must be at least 1");
        }
        this.remainingTicks = remainingTicks;
        this.stacks = stacks;
    }

    public static EffectInstance of(EffectId id, int durationTicks) {
        return new EffectInstance(id, durationTicks, 1);
    }

    @JsonProperty("id")
    public EffectId id() {
        return id;
    }

    @JsonProperty("remainingTicks")
    public int remainingTicks() {
        return remainingTicks;
    }

    @JsonProperty("stacks")
    public int stacks() {
        return stacks;
    }

    public void refresh(int durationTicks) {
        if (durationTicks < 0) {
            throw new IllegalArgumentException("Duration ticks must be non-negative");
        }
        remainingTicks = durationTicks;
    }

    public void stack(int durationTicks) {
        if (durationTicks < 0) {
            throw new IllegalArgumentException("Duration ticks must be non-negative");
        }
        stacks += 1;
        if (durationTicks > 0) {
            remainingTicks += durationTicks;
        }
    }

    public void replace(int durationTicks) {
        if (durationTicks < 0) {
            throw new IllegalArgumentException("Duration ticks must be non-negative");
        }
        stacks = 1;
        remainingTicks = durationTicks;
    }

    public void tickDown() {
        if (remainingTicks > 0) {
            remainingTicks -= 1;
        }
    }
}
