package io.taanielo.jmud.core.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityEffect;
import io.taanielo.jmud.core.ability.AbilityEffectKind;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityTargeting;
import io.taanielo.jmud.core.ability.AbilityType;
import io.taanielo.jmud.core.ability.repository.json.JsonAbilityRepository;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.CombatModifierResolver;
import io.taanielo.jmud.core.combat.CombatModifiers;
import io.taanielo.jmud.core.effects.repository.json.JsonEffectRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

/**
 * Verifies that the Shaman signature ability data loads from the real JSON definitions: the
 * {@code spell.ancestral-ward} ability is a room-wide {@code BENEFICIAL_GROUP} effect spell, and the
 * {@code ancestral-ward} effect applies a defensive buff to affected players.
 */
class AncestralWardBuffTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final AbilityId ANCESTRAL_WARD_ABILITY = AbilityId.of("spell.ancestral-ward");
    private static final EffectId ANCESTRAL_WARD_EFFECT = EffectId.of("ancestral-ward");

    @Test
    void ancestralWardAbilityLoadsAsGroupEffectSpell() throws Exception {
        JsonAbilityRepository repository = new JsonAbilityRepository(DATA_ROOT);

        Ability ability = repository.findById(ANCESTRAL_WARD_ABILITY)
            .orElseThrow(() -> new AssertionError("spell.ancestral-ward must be found"));

        assertEquals(AbilityType.SPELL, ability.type());
        assertEquals(AbilityTargeting.BENEFICIAL_GROUP, ability.targeting());
        assertEquals(1, ability.effects().size());
        AbilityEffect effect = ability.effects().getFirst();
        assertEquals(AbilityEffectKind.EFFECT, effect.kind());
        assertEquals("ancestral-ward", effect.effectId());
    }

    @Test
    void ancestralWardEffectLoadsWithDefensiveModifiers() throws Exception {
        EffectRepository repository = new JsonEffectRepository();

        EffectDefinition effect = repository.findById(ANCESTRAL_WARD_EFFECT).orElseThrow();

        assertEquals(80, effect.durationTicks());
        assertEquals(EffectStacking.REFRESH, effect.stacking());

        CombatModifiers modifiers = new CombatModifierResolver(repository)
            .resolve(List.of(EffectInstance.of(ANCESTRAL_WARD_EFFECT, effect.durationTicks())));
        assertEquals(2, modifiers.defense().add());
    }

    @Test
    void ancestralWardAppliesToPlayerAndIsVisibleInExamine() throws Exception {
        EffectRepository repository = new JsonEffectRepository();
        EffectEngine engine = new EffectEngine(repository);
        Player player = new Player(
            User.of(Username.of("thrall"), Password.hash("pw", 1000)),
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

        boolean applied = engine.apply(player, ANCESTRAL_WARD_EFFECT, sink);

        assertTrue(applied);
        assertEquals(1, player.effects().size());
        assertEquals(ANCESTRAL_WARD_EFFECT, player.effects().getFirst().id());
        assertEquals(80, player.effects().getFirst().remainingTicks());
        assertTrue(sink.targetMessages().stream().anyMatch(m -> m.contains("ancestors")),
            "Apply message should mention the ancestral ward");
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
