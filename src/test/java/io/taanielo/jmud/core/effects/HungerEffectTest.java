package io.taanielo.jmud.core.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;

class HungerEffectTest {

    @Test
    void sendsWarningOnThresholdCrossing() {
        List<String> messages = new ArrayList<>();
        MessageSink sink = messages::add;
        Player player = Player.of(User.of(Username.of("bob"), Password.of("pw")));
        EffectState state = EffectState.of(5, 10, 1, 4, 2);
        HungerEffect effect = new HungerEffect(player, sink, state);

        effect.tick();

        assertEquals(1, messages.size());
        assertEquals("You feel hungry.", messages.getFirst());
    }
}
