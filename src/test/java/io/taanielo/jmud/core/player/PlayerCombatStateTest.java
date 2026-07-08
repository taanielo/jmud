package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectInstance;

class PlayerCombatStateTest {

    @Test
    void effectsReturnsUnmodifiableView() {
        EffectInstance stoneskin = new EffectInstance(EffectId.of("stoneskin"), 5, 1);
        PlayerCombatState state = new PlayerCombatState(PlayerVitals.defaults(), List.of(stoneskin), false);

        List<EffectInstance> view = state.effects();

        assertThrows(UnsupportedOperationException.class,
            () -> view.add(new EffectInstance(EffectId.of("shield"), 3, 1)));
        assertThrows(UnsupportedOperationException.class, () -> view.remove(stoneskin));
        assertThrows(UnsupportedOperationException.class, view::clear);
    }

    @Test
    void effectsSnapshotDoesNotReflectLaterMutations() {
        PlayerCombatState state = new PlayerCombatState(PlayerVitals.defaults(), List.of(), false);

        List<EffectInstance> snapshot = state.effects();
        state.addEffect(new EffectInstance(EffectId.of("regen"), 10, 1));

        assertTrue(snapshot.isEmpty());
        assertEquals(1, state.effects().size());
    }

    @Test
    void addAndRemoveEffectMutateInternalState() {
        EffectInstance regen = new EffectInstance(EffectId.of("regen"), 10, 1);
        PlayerCombatState state = new PlayerCombatState(PlayerVitals.defaults(), List.of(), false);

        state.addEffect(regen);
        assertEquals(List.of(regen), state.effects());

        assertTrue(state.removeEffect(regen));
        assertTrue(state.effects().isEmpty());
        assertFalse(state.removeEffect(regen));
    }

    @Test
    void constructorTakesDefensiveCopyOfEffects() {
        List<EffectInstance> input = new ArrayList<>();
        input.add(new EffectInstance(EffectId.of("stoneskin"), 5, 1));
        PlayerCombatState state = new PlayerCombatState(PlayerVitals.defaults(), input, false);

        input.clear();

        assertEquals(1, state.effects().size());
    }

    @Test
    void stealthDefaultsToFalseAndToggles() {
        PlayerCombatState state = new PlayerCombatState(PlayerVitals.defaults(), List.of(), false);

        assertFalse(state.stealthActive());
        assertTrue(state.withStealth(true).stealthActive());
        assertFalse(state.withStealth(true).withStealth(false).stealthActive());
    }

    @Test
    void deathAndRespawnClearStealth() {
        PlayerCombatState hidden = new PlayerCombatState(PlayerVitals.defaults(), List.of(), false).withStealth(true);

        assertFalse(hidden.die().stealthActive());
        assertFalse(hidden.respawn().stealthActive());
    }

    @Test
    void withVitalsPreservesStealth() {
        PlayerCombatState hidden = new PlayerCombatState(PlayerVitals.defaults(), List.of(), false).withStealth(true);

        assertTrue(hidden.withVitals(hidden.vitals().damage(1)).stealthActive());
    }
}
