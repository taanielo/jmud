package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectInstance;

class PlayerDeathTest {

    @Test
    void dieClearsEffectsAndMarksDead() {
        PlayerVitals vitals = new PlayerVitals(5, 20, 10, 20, 10, 20);
        List<EffectInstance> effects = List.of(new EffectInstance(EffectId.of("stoneskin"), 3, 1));
        Player player = new Player(
            User.of(Username.of("sparky"), Password.hash("pw", 1000)),
            1,
            0,
            vitals,
            effects,
            "prompt",
            false,
            List.of(),
            null,
            null
        );

        Player dead = player.die();

        assertTrue(dead.isDead());
        assertEquals(0, dead.getVitals().hp());
        assertTrue(dead.effects().isEmpty());
    }

    @Test
    void respawnRestoresHalfVitals() {
        PlayerVitals vitals = new PlayerVitals(12, 20, 6, 20, 8, 20);
        Player player = new Player(
            User.of(Username.of("sparky"), Password.hash("pw", 1000)),
            1,
            0,
            vitals,
            List.of(),
            "prompt",
            false,
            List.of(),
            null,
            null
        );

        Player respawned = player.die().respawn();

        assertFalse(respawned.isDead());
        assertEquals(10, respawned.getVitals().hp());
        assertEquals(10, respawned.getVitals().mana());
        assertEquals(10, respawned.getVitals().move());
    }
}
