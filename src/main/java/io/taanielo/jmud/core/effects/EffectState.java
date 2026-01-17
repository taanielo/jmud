package io.taanielo.jmud.core.effects;

public class EffectState {
    private final int current;
    private final int max;
    private final int decayPerTick;
    private final int warningThreshold;
    private final int severeThreshold;

    public EffectState(int current, int max, int decayPerTick, int warningThreshold, int severeThreshold) {
        if (max < 0) {
            throw new IllegalArgumentException("Effect max must be non-negative");
        }
        if (current < 0) {
            throw new IllegalArgumentException("Effect current must be non-negative");
        }
        if (current > max) {
            throw new IllegalArgumentException("Effect current cannot exceed max");
        }
        if (decayPerTick < 0) {
            throw new IllegalArgumentException("Decay per tick must be non-negative");
        }
        if (warningThreshold < 0 || warningThreshold > max) {
            throw new IllegalArgumentException("Warning threshold must be within 0..max");
        }
        if (severeThreshold < 0 || severeThreshold > max) {
            throw new IllegalArgumentException("Severe threshold must be within 0..max");
        }
        if (severeThreshold > warningThreshold) {
            throw new IllegalArgumentException("Severe threshold must not exceed warning threshold");
        }
        this.current = current;
        this.max = max;
        this.decayPerTick = decayPerTick;
        this.warningThreshold = warningThreshold;
        this.severeThreshold = severeThreshold;
    }

    public static EffectState of(int current, int max, int decayPerTick, int warningThreshold, int severeThreshold) {
        return new EffectState(current, max, decayPerTick, warningThreshold, severeThreshold);
    }

    public int current() {
        return current;
    }

    public int max() {
        return max;
    }

    public int decayPerTick() {
        return decayPerTick;
    }

    public int warningThreshold() {
        return warningThreshold;
    }

    public int severeThreshold() {
        return severeThreshold;
    }

    public EffectState decay() {
        int next = Math.max(0, current - decayPerTick);
        return new EffectState(next, max, decayPerTick, warningThreshold, severeThreshold);
    }

    public EffectSeverity severity() {
        if (current <= severeThreshold) {
            return EffectSeverity.SEVERE;
        }
        if (current <= warningThreshold) {
            return EffectSeverity.WARNING;
        }
        return EffectSeverity.NORMAL;
    }
}
