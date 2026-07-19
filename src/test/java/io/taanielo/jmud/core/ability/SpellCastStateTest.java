package io.taanielo.jmud.core.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the channeled-cast mechanic (issue #693): a cast resolves only after its cast time
 * elapses, incoming damage interrupts it so the effect never applies and no cost is spent, and an
 * instant ability (cast time 0) never touches this state at all.
 */
class SpellCastStateTest {

    private static final AbilityId FIREBALL = AbilityId.of("spell.fireball.greater");

    @Test
    void resolvesEffectAfterCastTimeElapsesWithoutInterruption() {
        SpellCastState state = new SpellCastState();
        AtomicBoolean effectApplied = new AtomicBoolean(false);
        AtomicInteger mana = new AtomicInteger(20);
        AtomicBoolean interrupted = new AtomicBoolean(false);

        // onComplete models the real resolution: it applies the effect AND spends the cost.
        state.begin(FIREBALL, "greater fireball", 3,
            () -> {
                effectApplied.set(true);
                mana.addAndGet(-5);
            },
            () -> interrupted.set(true));

        assertTrue(state.isCasting());
        assertEquals(3, state.ticksRemaining());

        state.tick();
        assertTrue(state.isCasting(), "cast should still be channeling after one tick");
        assertFalse(effectApplied.get());
        assertEquals(2, state.ticksRemaining());

        state.tick();
        assertTrue(state.isCasting());
        assertFalse(effectApplied.get());

        state.tick();
        // Countdown reached zero: the effect resolves and the cost is spent, and the cast clears.
        assertTrue(effectApplied.get(), "effect should apply once the cast time elapses");
        assertEquals(15, mana.get(), "cost should be spent on a completed cast");
        assertFalse(interrupted.get());
        assertFalse(state.isCasting());
        assertEquals(0, state.ticksRemaining());
    }

    @Test
    void interruptionCancelsEffectAndSpendsNoCost() {
        SpellCastState state = new SpellCastState();
        AtomicBoolean effectApplied = new AtomicBoolean(false);
        AtomicInteger mana = new AtomicInteger(20);
        AtomicBoolean interrupted = new AtomicBoolean(false);

        state.begin(FIREBALL, "greater fireball", 3,
            () -> {
                effectApplied.set(true);
                mana.addAndGet(-5);
            },
            () -> interrupted.set(true));

        state.tick();
        // Damage lands mid-cast.
        state.interrupt();

        assertTrue(interrupted.get(), "interrupt action should run");
        assertFalse(effectApplied.get(), "effect must never apply on an interrupted cast");
        assertEquals(20, mana.get(), "no mana may be spent on a fizzled cast");
        assertFalse(state.isCasting());

        // Further ticks must not resurrect the cancelled cast's completion.
        state.tick();
        state.tick();
        state.tick();
        assertFalse(effectApplied.get());
    }

    @Test
    void cancelSilentlyRunsNeitherCallback() {
        SpellCastState state = new SpellCastState();
        AtomicBoolean effectApplied = new AtomicBoolean(false);
        AtomicBoolean interrupted = new AtomicBoolean(false);

        state.begin(FIREBALL, "greater fireball", 2,
            () -> effectApplied.set(true),
            () -> interrupted.set(true));

        state.cancelSilently();

        assertFalse(state.isCasting());
        assertFalse(effectApplied.get());
        assertFalse(interrupted.get());
    }

    @Test
    void tickAndInterruptAreNoOpsWhenIdle() {
        SpellCastState state = new SpellCastState();
        assertFalse(state.isCasting());
        // Neither of these should throw when there is nothing being cast.
        state.tick();
        state.interrupt();
        state.cancelSilently();
        assertFalse(state.isCasting());
    }

    @Test
    void beginRejectsNonPositiveCastTime() {
        SpellCastState state = new SpellCastState();
        assertThrows(IllegalArgumentException.class, () ->
            state.begin(FIREBALL, "greater fireball", 0, () -> { }, () -> { }));
    }
}
