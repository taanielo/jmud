package io.taanielo.jmud.core.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.MessageChannel;
import io.taanielo.jmud.core.messaging.MessagePhase;
import io.taanielo.jmud.core.messaging.MessageSpec;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

class EffectEngineTest {

    @Test
    void refreshStackingResetsDuration() throws EffectRepositoryException {
        EffectId id = EffectId.of("stoneskin");
        EffectDefinition definition = new EffectDefinition(
            id,
            "Stoneskin",
            5,
            1,
            EffectStacking.REFRESH,
            List.of(),
            null
        );
        EffectEngine engine = new EffectEngine(new InMemoryEffectRepository(definition));
        Player player = new Player(
            User.of(Username.of("bob"), Password.hash("pw", 1000)),
            1,
            0,
            PlayerVitals.defaults(),
            new ArrayList<>(List.of(new EffectInstance(id, 1, 1))),
            "HP {hp}/{maxHp}",
            false,
            List.of(),
            null,
            null
        );

        boolean applied = engine.apply(player, id, new RecordingSink());

        assertEquals(5, player.effects().getFirst().remainingTicks());
        assertTrue(applied);
    }

    @Test
    void expiresEffectsAndSendsMessage() throws EffectRepositoryException {
        EffectId id = EffectId.of("shield");
        List<MessageSpec> messages = List.of(
            new MessageSpec(MessagePhase.EXPIRE, MessageChannel.SELF, "Shield fades.")
        );
        EffectDefinition definition = new EffectDefinition(
            id,
            "Shield",
            2,
            1,
            EffectStacking.REFRESH,
            List.of(),
            messages
        );
        EffectEngine engine = new EffectEngine(new InMemoryEffectRepository(definition));
        Player player = new Player(
            User.of(Username.of("ana"), Password.hash("pw", 1000)),
            1,
            0,
            PlayerVitals.defaults(),
            new ArrayList<>(List.of(new EffectInstance(id, 1, 1))),
            "HP {hp}/{maxHp}",
            false,
            List.of(),
            null,
            null
        );
        RecordingSink sink = new RecordingSink();

        engine.tick(player, sink);

        assertTrue(player.effects().isEmpty());
        assertEquals(List.of("Shield fades."), sink.messages());
    }

    private static class InMemoryEffectRepository implements EffectRepository {
        private final EffectDefinition definition;

        private InMemoryEffectRepository(EffectDefinition definition) {
            this.definition = definition;
        }

        @Override
        public Optional<EffectDefinition> findById(EffectId id) {
            if (definition.id().equals(id)) {
                return Optional.of(definition);
            }
            return Optional.empty();
        }
    }

    private static class RecordingSink implements EffectMessageSink {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void sendToTarget(String message) {
            messages.add(message);
        }

        public List<String> messages() {
            return List.copyOf(messages);
        }
    }
}
