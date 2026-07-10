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
 * Verifies that the Bard signature ability data loads from the real JSON definitions: the
 * {@code spell.battle-hymn} ability is a room-wide {@code BENEFICIAL_GROUP} effect spell, and the
 * {@code battle-hymn} effect applies bonus attack and critical-hit chance to affected players.
 */
class BattleHymnBuffTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final AbilityId BATTLE_HYMN_ABILITY = AbilityId.of("spell.battle-hymn");
    private static final EffectId BATTLE_HYMN_EFFECT = EffectId.of("battle-hymn");

    @Test
    void battleHymnAbilityLoadsAsGroupEffectSpell() throws Exception {
        JsonAbilityRepository repository = new JsonAbilityRepository(DATA_ROOT);

        Ability ability = repository.findById(BATTLE_HYMN_ABILITY)
            .orElseThrow(() -> new AssertionError("spell.battle-hymn must be found"));

        assertEquals(AbilityType.SPELL, ability.type());
        assertEquals(AbilityTargeting.BENEFICIAL_GROUP, ability.targeting());
        assertEquals(1, ability.effects().size());
        AbilityEffect effect = ability.effects().getFirst();
        assertEquals(AbilityEffectKind.EFFECT, effect.kind());
        assertEquals("battle-hymn", effect.effectId());
    }

    @Test
    void battleHymnEffectLoadsWithAttackAndCritModifiers() throws Exception {
        EffectRepository repository = new JsonEffectRepository();

        EffectDefinition effect = repository.findById(BATTLE_HYMN_EFFECT).orElseThrow();

        assertEquals(80, effect.durationTicks());
        assertEquals(EffectStacking.REFRESH, effect.stacking());

        CombatModifiers modifiers = new CombatModifierResolver(repository)
            .resolve(List.of(EffectInstance.of(BATTLE_HYMN_EFFECT, effect.durationTicks())));
        assertEquals(2, modifiers.attack().add());
        assertEquals(5, modifiers.critChance().add());
    }

    @Test
    void battleHymnAppliesToPlayerAndIsVisibleInExamine() throws Exception {
        EffectRepository repository = new JsonEffectRepository();
        EffectEngine engine = new EffectEngine(repository);
        Player player = new Player(
            User.of(Username.of("merry"), Password.hash("pw", 1000)),
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

        boolean applied = engine.apply(player, BATTLE_HYMN_EFFECT, sink);

        assertTrue(applied);
        assertEquals(1, player.effects().size());
        assertEquals(BATTLE_HYMN_EFFECT, player.effects().getFirst().id());
        assertEquals(80, player.effects().getFirst().remainingTicks());
        assertTrue(sink.targetMessages().stream().anyMatch(m -> m.contains("battle hymn")),
            "Apply message should mention the battle hymn");
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
