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
import io.taanielo.jmud.core.effects.repository.json.JsonEffectRepository;
import io.taanielo.jmud.core.healing.HealingEngine;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

/**
 * Verifies the Druid signature spell data: {@code spell.regrowth} loads as a beneficial effect spell,
 * the {@code regrowth} effect applies a periodic {@code heal_per_tick} modifier, and casting it heals
 * the target gradually over ticks (capping at max HP) rather than instantly.
 */
class RegrowthSpellTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final AbilityId REGROWTH_ABILITY = AbilityId.of("spell.regrowth");
    private static final EffectId REGROWTH_EFFECT = EffectId.of("regrowth");

    @Test
    void regrowthAbilityLoadsAsBeneficialEffectSpell() throws Exception {
        JsonAbilityRepository repository = new JsonAbilityRepository(DATA_ROOT);

        Ability ability = repository.findById(REGROWTH_ABILITY)
            .orElseThrow(() -> new AssertionError("spell.regrowth must be found"));

        assertEquals(AbilityType.SPELL, ability.type());
        assertEquals(AbilityTargeting.BENEFICIAL, ability.targeting());
        assertEquals(1, ability.effects().size());
        AbilityEffect effect = ability.effects().getFirst();
        assertEquals(AbilityEffectKind.EFFECT, effect.kind());
        assertEquals("regrowth", effect.effectId());
    }

    @Test
    void regrowthEffectDefinesHealPerTickModifier() throws Exception {
        EffectRepository repository = new JsonEffectRepository();

        EffectDefinition effect = repository.findById(REGROWTH_EFFECT).orElseThrow();

        assertEquals(EffectStacking.REFRESH, effect.stacking());
        assertTrue(effect.durationTicks() > 1, "Regrowth must last multiple ticks");
        assertTrue(
            effect.modifiers().stream().anyMatch(m -> m.stat().equalsIgnoreCase("heal_per_tick") && m.amount() > 0),
            "Regrowth must define a positive heal_per_tick modifier"
        );
    }

    @Test
    void castingRegrowthAppliesEffectToTarget() throws Exception {
        EffectRepository repository = new JsonEffectRepository();
        EffectEngine engine = new EffectEngine(repository);
        Player target = newPlayer(20, 20);
        RecordingSink sink = new RecordingSink();

        boolean applied = engine.apply(target, REGROWTH_EFFECT, sink);

        assertTrue(applied);
        assertEquals(1, target.effects().size());
        assertEquals(REGROWTH_EFFECT, target.effects().getFirst().id());
        assertTrue(sink.targetMessages().stream().anyMatch(m -> m.toLowerCase().contains("regrow")),
            "Apply message should describe the regrowth");
    }

    @Test
    void regrowthHealsGraduallyAndCapsAtMaxHp() throws Exception {
        EffectRepository repository = new JsonEffectRepository();
        HealingEngine healingEngine = new HealingEngine(repository);
        int healPerTick = repository.findById(REGROWTH_EFFECT).orElseThrow()
            .modifiers().stream()
            .filter(m -> m.stat().equalsIgnoreCase("heal_per_tick"))
            .mapToInt(EffectModifier::amount)
            .sum();

        List<EffectInstance> effects = new ArrayList<>();
        effects.add(EffectInstance.of(REGROWTH_EFFECT, 8));
        Player player = new Player(
            User.of(Username.of("Radagast"), Password.hash("pw", 1000)),
            1,
            0,
            new PlayerVitals(10, 20, 20, 20, 20, 20),
            effects,
            "HP {hp}/{maxHp}",
            false,
            List.of(),
            null,
            null
        );

        // Base per-tick healing is disabled (0) so the whole gain is attributable to the effect.
        Player afterOneTick = healingEngine.apply(player, 0);
        assertEquals(10 + healPerTick, afterOneTick.getVitals().hp(),
            "Regrowth should heal exactly its heal_per_tick amount, not instantly to full");

        // Drive many ticks; HP must saturate at max and never exceed it.
        Player current = afterOneTick;
        for (int i = 0; i < 20; i++) {
            current = healingEngine.apply(current, 0);
        }
        assertEquals(20, current.getVitals().hp(), "Regrowth must cap healing at max HP");
    }

    private static Player newPlayer(int hp, int maxHp) {
        return new Player(
            User.of(Username.of("Radagast"), Password.hash("pw", 1000)),
            1,
            0,
            new PlayerVitals(hp, maxHp, 20, 20, 20, 20),
            new ArrayList<>(),
            "HP {hp}/{maxHp}",
            false,
            List.of(),
            null,
            null
        );
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
