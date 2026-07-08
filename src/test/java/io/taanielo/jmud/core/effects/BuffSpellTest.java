package io.taanielo.jmud.core.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.CombatModifierResolver;
import io.taanielo.jmud.core.combat.CombatModifiers;
import io.taanielo.jmud.core.effects.repository.json.JsonEffectRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

/**
 * Verifies that the data-driven buff effects (bless, stoneskin, haste) load from the
 * real {@code effects/} JSON definitions, apply with correct stat modifiers, tick down
 * over time, refresh on recast, and coexist on the same player.
 */
class BuffSpellTest {

    private static final EffectId BLESS = EffectId.of("bless");
    private static final EffectId STONESKIN = EffectId.of("stoneskin");
    private static final EffectId HASTE = EffectId.of("haste");

    private static EffectRepository realRepository() throws EffectRepositoryException {
        return new JsonEffectRepository();
    }

    private static Player playerWithEffects(String name, EffectInstance... effects) {
        return new Player(
            User.of(Username.of(name), Password.hash("pw", 1000)),
            1,
            0,
            PlayerVitals.defaults(),
            new ArrayList<>(List.of(effects)),
            "HP {hp}/{maxHp}",
            false,
            List.of(),
            null,
            null
        );
    }

    @Test
    void blessDefinitionLoadsWithExpectedModifiersAndDuration() throws EffectRepositoryException {
        EffectRepository repository = realRepository();

        EffectDefinition bless = repository.findById(BLESS).orElseThrow();

        assertEquals(100, bless.durationTicks());
        assertEquals(EffectStacking.REFRESH, bless.stacking());
        CombatModifiers modifiers = new CombatModifierResolver(repository)
            .resolve(List.of(EffectInstance.of(BLESS, bless.durationTicks())));
        assertEquals(2, modifiers.defense().add());
        assertEquals(1, modifiers.hitChance().add());
    }

    @Test
    void hasteDefinitionLoadsWithExpectedModifiersAndDuration() throws EffectRepositoryException {
        EffectRepository repository = realRepository();

        EffectDefinition haste = repository.findById(HASTE).orElseThrow();

        assertEquals(80, haste.durationTicks());
        assertEquals(EffectStacking.REFRESH, haste.stacking());
        CombatModifiers modifiers = new CombatModifierResolver(repository)
            .resolve(List.of(EffectInstance.of(HASTE, haste.durationTicks())));
        assertEquals(2, modifiers.attack().add());
    }

    @Test
    void buffAppliesWithApplyMessageAndFullDuration() throws EffectRepositoryException {
        EffectRepository repository = realRepository();
        EffectEngine engine = new EffectEngine(repository);
        Player player = playerWithEffects("aria");
        RecordingSink sink = new RecordingSink();

        boolean applied = engine.apply(player, BLESS, sink);

        assertTrue(applied);
        assertEquals(1, player.effects().size());
        assertEquals(BLESS, player.effects().getFirst().id());
        assertEquals(100, player.effects().getFirst().remainingTicks());
        assertTrue(sink.targetMessages().contains("You feel blessed."));
    }

    @Test
    void buffTicksDownEachTickAndExpiresWithMessage() throws EffectRepositoryException {
        EffectRepository repository = realRepository();
        EffectEngine engine = new EffectEngine(repository);
        Player player = playerWithEffects("brue", new EffectInstance(BLESS, 3, 1));
        RecordingSink sink = new RecordingSink();

        engine.tick(player, sink);
        assertEquals(2, player.effects().getFirst().remainingTicks());
        engine.tick(player, sink);
        assertEquals(1, player.effects().getFirst().remainingTicks());
        engine.tick(player, sink);

        assertTrue(player.effects().isEmpty());
        assertTrue(sink.targetMessages().contains("The blessing fades."));
    }

    @Test
    void recastRefreshesDuration() throws EffectRepositoryException {
        EffectRepository repository = realRepository();
        EffectEngine engine = new EffectEngine(repository);
        Player player = playerWithEffects("cade", new EffectInstance(BLESS, 4, 1));
        RecordingSink sink = new RecordingSink();

        boolean applied = engine.apply(player, BLESS, sink);

        assertTrue(applied);
        assertEquals(1, player.effects().size());
        assertEquals(100, player.effects().getFirst().remainingTicks());
    }

    @Test
    void multipleDifferentBuffsCoexist() throws EffectRepositoryException {
        EffectRepository repository = realRepository();
        EffectEngine engine = new EffectEngine(repository);
        Player player = playerWithEffects("dorn");
        RecordingSink sink = new RecordingSink();

        engine.apply(player, BLESS, sink);
        engine.apply(player, STONESKIN, sink);
        engine.apply(player, HASTE, sink);

        List<EffectId> activeIds = player.effects().stream().map(EffectInstance::id).toList();
        assertEquals(3, activeIds.size());
        assertTrue(activeIds.contains(BLESS));
        assertTrue(activeIds.contains(STONESKIN));
        assertTrue(activeIds.contains(HASTE));
    }

    @Test
    void expiredBuffNoLongerContributesModifiers() throws EffectRepositoryException {
        EffectRepository repository = realRepository();
        EffectEngine engine = new EffectEngine(repository);
        Player player = playerWithEffects("elle", new EffectInstance(HASTE, 1, 1));
        RecordingSink sink = new RecordingSink();

        engine.tick(player, sink);

        assertTrue(player.effects().isEmpty());
        CombatModifiers modifiers = new CombatModifierResolver(repository).resolve(player.effects());
        assertEquals(0, modifiers.attack().add());
        assertFalse(sink.targetMessages().isEmpty());
    }

    private static final class RecordingSink implements EffectMessageSink {
        private final List<String> targetMessages = new ArrayList<>();

        @Override
        public void sendToTarget(String message) {
            targetMessages.add(message);
        }

        List<String> targetMessages() {
            return List.copyOf(targetMessages);
        }
    }
}
