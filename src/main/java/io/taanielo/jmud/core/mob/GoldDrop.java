package io.taanielo.jmud.core.mob;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Defines the gold-drop range for a mob template.
 *
 * <p>When a mob carrying a {@code GoldDrop} is killed, a random integer
 * in {@code [min, max]} (inclusive) is awarded to the killing player.
 */
public record GoldDrop(int min, int max) {

    public GoldDrop {
        if (min < 0) {
            throw new IllegalArgumentException("GoldDrop min must be non-negative, got " + min);
        }
        if (max < min) {
            throw new IllegalArgumentException(
                "GoldDrop max must be >= min, got min=" + min + " max=" + max);
        }
    }

    /**
     * Rolls a random gold amount within {@code [min, max]} using the calling
     * thread's {@link ThreadLocalRandom}.
     *
     * @return a value in {@code [min, max]} inclusive
     */
    public int roll() {
        if (min == max) {
            return min;
        }
        return min + ThreadLocalRandom.current().nextInt(max - min + 1);
    }
}
