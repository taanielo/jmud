package io.taanielo.jmud.core.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EffectStateTest {

    @Test
    void decaysAndUpdatesSeverity() {
        EffectState state = EffectState.of(5, 10, 1, 4, 2);
        assertEquals(EffectSeverity.NORMAL, state.severity());

        EffectState warning = state.decay();
        assertEquals(4, warning.current());
        assertEquals(EffectSeverity.WARNING, warning.severity());

        EffectState severe = warning.decay().decay();
        assertEquals(2, severe.current());
        assertEquals(EffectSeverity.SEVERE, severe.severity());
    }
}
