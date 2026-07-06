package io.taanielo.jmud.core.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectInstance;
import io.taanielo.jmud.core.effects.EffectStacking;
import io.taanielo.jmud.core.messaging.MessageChannel;
import io.taanielo.jmud.core.messaging.MessagePhase;
import io.taanielo.jmud.core.messaging.MessageSpec;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

class DefaultAbilityEffectResolverTest {

    private final EffectId poisonId = EffectId.of("poison");
    private final EffectDefinition poisonDefinition = new EffectDefinition(
        poisonId,
        "Poison",
        10,
        1,
        EffectStacking.REFRESH,
        List.of(),
        List.of(new MessageSpec(MessagePhase.EXPIRE, MessageChannel.SELF, "The poison leaves your body."))
    );
    private final EffectEngine effectEngine = new EffectEngine(id -> id.equals(poisonId)
        ? Optional.of(poisonDefinition)
        : Optional.empty());
    private final RecordingAbilityMessageSink messageSink = new RecordingAbilityMessageSink();
    private final RecordingAbilityEffectListener listener = new RecordingAbilityEffectListener();
    private final DefaultAbilityEffectResolver resolver =
        new DefaultAbilityEffectResolver(effectEngine, messageSink, listener);

    @Test
    void cureRemovesActiveEffectAndNotifiesListener() {
        Player poisoned = playerWithEffect(new EffectInstance(poisonId, 5, 1));
        AbilityContext context = new AbilityContext(poisoned, poisoned);
        AbilityEffect cure = new AbilityEffect(AbilityEffectKind.CURE, null, null, 0, "poison");

        resolver.apply(cure, context);

        assertTrue(context.target().effects().isEmpty());
        assertTrue(listener.applied);
        assertEquals(List.of("The poison leaves your body."), messageSink.targetMessages);
    }

    @Test
    void cureOnPlayerWithoutMatchingEffectDoesNothing() {
        Player healthy = playerWithEffect(null);
        AbilityContext context = new AbilityContext(healthy, healthy);
        AbilityEffect cure = new AbilityEffect(AbilityEffectKind.CURE, null, null, 0, "poison");

        resolver.apply(cure, context);

        assertTrue(context.target().effects().isEmpty());
        assertTrue(messageSink.targetMessages.isEmpty());
        assertEquals(false, listener.applied);
    }

    private Player playerWithEffect(EffectInstance instance) {
        List<EffectInstance> effects = new ArrayList<>();
        if (instance != null) {
            effects.add(instance);
        }
        return new Player(
            User.of(Username.of("eve"), Password.hash("pw", 1000)),
            1,
            0,
            PlayerVitals.defaults(),
            effects,
            "HP {hp}/{maxHp}",
            false,
            List.of(),
            null,
            null
        );
    }

    private static class RecordingAbilityMessageSink implements AbilityMessageSink {
        private final List<String> targetMessages = new ArrayList<>();

        @Override
        public void sendToSource(Player source, String message) {
        }

        @Override
        public void sendToTarget(Player target, String message) {
            targetMessages.add(message);
        }

        @Override
        public void sendToRoom(Player source, Player target, String message) {
        }
    }

    private static class RecordingAbilityEffectListener implements AbilityEffectListener {
        private boolean applied = false;

        @Override
        public void onApplied(AbilityEffect effect, AbilityContext context) {
            applied = true;
        }
    }

}
