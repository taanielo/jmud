package io.taanielo.jmud.core.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    @Test
    void removesActiveEffectAndSendsExpireMessage() throws EffectRepositoryException {
        EffectId id = EffectId.of("poison");
        List<MessageSpec> messages = List.of(
            new MessageSpec(MessagePhase.EXPIRE, MessageChannel.SELF, "The poison leaves your body.")
        );
        EffectDefinition definition = new EffectDefinition(
            id,
            "Poison",
            10,
            1,
            EffectStacking.REFRESH,
            List.of(),
            messages
        );
        EffectEngine engine = new EffectEngine(new InMemoryEffectRepository(definition));
        Player player = new Player(
            User.of(Username.of("cara"), Password.hash("pw", 1000)),
            1,
            0,
            PlayerVitals.defaults(),
            new ArrayList<>(List.of(new EffectInstance(id, 1, 5))),
            "HP {hp}/{maxHp}",
            false,
            List.of(),
            null,
            null
        );
        RecordingSink sink = new RecordingSink();

        boolean removed = engine.remove(player, id, sink);

        assertTrue(removed);
        assertTrue(player.effects().isEmpty());
        assertEquals(List.of("The poison leaves your body."), sink.messages());
    }

    @Test
    void removeReturnsFalseWhenEffectNotActive() throws EffectRepositoryException {
        EffectId id = EffectId.of("poison");
        EffectDefinition definition = new EffectDefinition(
            id,
            "Poison",
            10,
            1,
            EffectStacking.REFRESH,
            List.of(),
            List.of()
        );
        EffectEngine engine = new EffectEngine(new InMemoryEffectRepository(definition));
        Player player = new Player(
            User.of(Username.of("dan"), Password.hash("pw", 1000)),
            1,
            0,
            PlayerVitals.defaults(),
            new ArrayList<>(),
            "HP {hp}/{maxHp}",
            false,
            List.of(),
            null,
            null
        );
        RecordingSink sink = new RecordingSink();

        boolean removed = engine.remove(player, id, sink);

        assertTrue(player.effects().isEmpty());
        assertEquals(List.of(), sink.messages());
        assertEquals(false, removed);
    }

    @Test
    void examineLinesRendersExaminePhaseAndSkipsEffectsWithoutOne() throws EffectRepositoryException {
        EffectId blessId = EffectId.of("bless");
        EffectId plainId = EffectId.of("plain");
        EffectDefinition bless = new EffectDefinition(
            blessId,
            "Bless",
            10,
            1,
            EffectStacking.REFRESH,
            List.of(),
            List.of(new MessageSpec(MessagePhase.EXAMINE, MessageChannel.ROOM, "{name} is blessed."))
        );
        EffectDefinition plain = new EffectDefinition(
            plainId,
            "Plain",
            10,
            1,
            EffectStacking.REFRESH,
            List.of(),
            List.of(new MessageSpec(MessagePhase.APPLY, MessageChannel.SELF, "You feel plain."))
        );
        EffectEngine engine = new EffectEngine(
            new MapEffectRepository(Map.of(blessId, bless, plainId, plain)));
        Player player = new Player(
            User.of(Username.of("bob"), Password.hash("pw", 1000)),
            1,
            0,
            PlayerVitals.defaults(),
            new ArrayList<>(List.of(new EffectInstance(blessId, 5, 1), new EffectInstance(plainId, 5, 1))),
            "HP {hp}/{maxHp}",
            false,
            List.of(),
            null,
            null
        );

        List<String> lines = engine.examineLines(player);

        assertEquals(List.of("bob is blessed."), lines);
    }

    @Test
    void examineLinesEmptyWhenNoActiveEffects() throws EffectRepositoryException {
        EffectDefinition bless = new EffectDefinition(
            EffectId.of("bless"),
            "Bless",
            10,
            1,
            EffectStacking.REFRESH,
            List.of(),
            List.of(new MessageSpec(MessagePhase.EXAMINE, MessageChannel.ROOM, "{name} is blessed."))
        );
        EffectEngine engine = new EffectEngine(new InMemoryEffectRepository(bless));
        Player player = new Player(
            User.of(Username.of("eve"), Password.hash("pw", 1000)),
            1,
            0,
            PlayerVitals.defaults(),
            new ArrayList<>(),
            "HP {hp}/{maxHp}",
            false,
            List.of(),
            null,
            null
        );

        assertTrue(engine.examineLines(player).isEmpty());
    }

    @Test
    void activeControlFindsMatchingControlEffect() throws EffectRepositoryException {
        EffectId id = EffectId.of("rooted");
        EffectDefinition definition = new EffectDefinition(
            id,
            "Rooted",
            6,
            1,
            EffectStacking.REFRESH,
            List.of(),
            List.of(),
            ControlType.ROOT
        );
        EffectEngine engine = new EffectEngine(new InMemoryEffectRepository(definition));
        Player player = controlledPlayer("frank", id);

        Optional<EffectDefinition> found =
            engine.activeControl(player, java.util.EnumSet.of(ControlType.ROOT, ControlType.STUN));

        assertTrue(found.isPresent());
        assertEquals("Rooted", found.get().name());
    }

    @Test
    void activeControlIgnoresNonMatchingControlType() throws EffectRepositoryException {
        EffectId id = EffectId.of("rooted");
        EffectDefinition definition = new EffectDefinition(
            id,
            "Rooted",
            6,
            1,
            EffectStacking.REFRESH,
            List.of(),
            List.of(),
            ControlType.ROOT
        );
        EffectEngine engine = new EffectEngine(new InMemoryEffectRepository(definition));
        Player player = controlledPlayer("gwen", id);

        Optional<EffectDefinition> found =
            engine.activeControl(player, java.util.EnumSet.of(ControlType.SILENCE));

        assertTrue(found.isEmpty());
    }

    @Test
    void activeControlEmptyWhenEffectHasNoControlClassification() throws EffectRepositoryException {
        EffectId id = EffectId.of("stoneskin");
        EffectDefinition definition = new EffectDefinition(
            id,
            "Stoneskin",
            6,
            1,
            EffectStacking.REFRESH,
            List.of(),
            List.of()
        );
        EffectEngine engine = new EffectEngine(new InMemoryEffectRepository(definition));
        Player player = controlledPlayer("hank", id);

        Optional<EffectDefinition> found =
            engine.activeControl(player, java.util.EnumSet.of(ControlType.ROOT, ControlType.STUN));

        assertTrue(found.isEmpty());
    }

    @Test
    void controlMatrixDeniesMatchingActionsAndPermitsOthers() throws EffectRepositoryException {
        // Blocking sets mirror SocketCommandContextImpl.ControlledAction: MOVE/FLEE -> {ROOT, STUN},
        // CAST -> {SILENCE, STUN}, USE -> {STUN}. A player carrying exactly one control effect must be
        // denied only the actions that effect gates, and permitted the rest.
        java.util.Set<ControlType> move = java.util.EnumSet.of(ControlType.ROOT, ControlType.STUN);
        java.util.Set<ControlType> flee = java.util.EnumSet.of(ControlType.ROOT, ControlType.STUN);
        java.util.Set<ControlType> cast = java.util.EnumSet.of(ControlType.SILENCE, ControlType.STUN);
        java.util.Set<ControlType> use = java.util.EnumSet.of(ControlType.STUN);

        EffectId rootId = EffectId.of("rooted");
        EffectId silenceId = EffectId.of("silenced");
        EffectId stunId = EffectId.of("hammer-of-justice");
        EffectEngine engine = new EffectEngine(new MapEffectRepository(Map.of(
            rootId, controlDefinition(rootId, "Rooted", ControlType.ROOT),
            silenceId, controlDefinition(silenceId, "Silenced", ControlType.SILENCE),
            stunId, controlDefinition(stunId, "Stunned", ControlType.STUN)
        )));

        Player rooted = controlledPlayer("rooter", rootId);
        assertTrue(engine.activeControl(rooted, move).isPresent());
        assertTrue(engine.activeControl(rooted, flee).isPresent());
        assertTrue(engine.activeControl(rooted, cast).isEmpty());
        assertTrue(engine.activeControl(rooted, use).isEmpty());

        Player silenced = controlledPlayer("silencer", silenceId);
        assertTrue(engine.activeControl(silenced, move).isEmpty());
        assertTrue(engine.activeControl(silenced, flee).isEmpty());
        assertTrue(engine.activeControl(silenced, cast).isPresent());
        assertTrue(engine.activeControl(silenced, use).isEmpty());

        Player stunned = controlledPlayer("stunned", stunId);
        assertTrue(engine.activeControl(stunned, move).isPresent());
        assertTrue(engine.activeControl(stunned, flee).isPresent());
        assertTrue(engine.activeControl(stunned, cast).isPresent());
        assertTrue(engine.activeControl(stunned, use).isPresent());

        Player uncontrolled = controlledPlayer("free", EffectId.of("stoneskin"));
        EffectEngine plainEngine = new EffectEngine(new InMemoryEffectRepository(
            new EffectDefinition(EffectId.of("stoneskin"), "Stoneskin", 6, 1,
                EffectStacking.REFRESH, List.of(), List.of())));
        assertTrue(plainEngine.activeControl(uncontrolled, move).isEmpty());
        assertTrue(plainEngine.activeControl(uncontrolled, cast).isEmpty());
        assertTrue(plainEngine.activeControl(uncontrolled, use).isEmpty());
    }

    private static EffectDefinition controlDefinition(EffectId id, String name, ControlType control) {
        return new EffectDefinition(id, name, 6, 1, EffectStacking.REFRESH, List.of(), List.of(), control);
    }

    private static Player controlledPlayer(String username, EffectId activeEffect) {
        return new Player(
            User.of(Username.of(username), Password.hash("pw", 1000)),
            1,
            0,
            PlayerVitals.defaults(),
            new ArrayList<>(List.of(new EffectInstance(activeEffect, 5, 1))),
            "HP {hp}/{maxHp}",
            false,
            List.of(),
            null,
            null
        );
    }

    private static class MapEffectRepository implements EffectRepository {
        private final Map<EffectId, EffectDefinition> definitions;

        private MapEffectRepository(Map<EffectId, EffectDefinition> definitions) {
            this.definitions = definitions;
        }

        @Override
        public Optional<EffectDefinition> findById(EffectId id) {
            return Optional.ofNullable(definitions.get(id));
        }
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

        List<String> messages() {
            return List.copyOf(messages);
        }
    }
}
