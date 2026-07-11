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
 * Verifies that the Paladin signature ward loads from the real JSON definitions: the
 * {@code spell.divine-shield} ability is a self/ally-targeted {@code BENEFICIAL} effect spell with a
 * cooldown longer than the ward's duration, and the {@code divine-shield} effect applies a strong
 * defensive buff that makes the target measurably harder to hit and expires after its duration.
 */
class DivineShieldBuffTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final AbilityId DIVINE_SHIELD_ABILITY = AbilityId.of("spell.divine-shield");
    private static final EffectId DIVINE_SHIELD_EFFECT = EffectId.of("divine-shield");

    @Test
    void divineShieldAbilityLoadsAsBeneficialEffectSpell() throws Exception {
        JsonAbilityRepository repository = new JsonAbilityRepository(DATA_ROOT);

        Ability ability = repository.findById(DIVINE_SHIELD_ABILITY)
            .orElseThrow(() -> new AssertionError("spell.divine-shield must be found"));

        assertEquals(AbilityType.SPELL, ability.type());
        assertEquals(AbilityTargeting.BENEFICIAL, ability.targeting());
        assertEquals(1, ability.effects().size());
        AbilityEffect effect = ability.effects().getFirst();
        assertEquals(AbilityEffectKind.EFFECT, effect.kind());
        assertEquals("divine-shield", effect.effectId());
    }

    @Test
    void divineShieldCooldownOutlastsWardDuration() throws Exception {
        JsonAbilityRepository abilities = new JsonAbilityRepository(DATA_ROOT);
        EffectRepository effects = new JsonEffectRepository();

        Ability ability = abilities.findById(DIVINE_SHIELD_ABILITY).orElseThrow();
        EffectDefinition effect = effects.findById(DIVINE_SHIELD_EFFECT).orElseThrow();

        assertTrue(ability.cooldown().ticks() > effect.durationTicks(),
            "Divine Shield cooldown must outlast its own ward so it reads as a clutch cooldown");
    }

    @Test
    void divineShieldEffectLoadsWithStrongDefensiveModifiers() throws Exception {
        EffectRepository repository = new JsonEffectRepository();

        EffectDefinition effect = repository.findById(DIVINE_SHIELD_EFFECT).orElseThrow();

        assertEquals(12, effect.durationTicks());
        assertEquals(EffectStacking.REFRESH, effect.stacking());

        CombatModifiers modifiers = new CombatModifierResolver(repository)
            .resolve(List.of(EffectInstance.of(DIVINE_SHIELD_EFFECT, effect.durationTicks())));
        assertTrue(modifiers.defense().add() >= 6,
            "Divine Shield should grant a noticeable defensive spike");
    }

    @Test
    void divineShieldAppliesToPlayerAndImprovesDefense() throws Exception {
        EffectRepository repository = new JsonEffectRepository();
        EffectEngine engine = new EffectEngine(repository);
        Player player = paladinPlayer();
        RecordingSink sink = new RecordingSink();

        boolean applied = engine.apply(player, DIVINE_SHIELD_EFFECT, sink);

        assertTrue(applied);
        assertEquals(1, player.effects().size());
        assertEquals(DIVINE_SHIELD_EFFECT, player.effects().getFirst().id());
        assertEquals(12, player.effects().getFirst().remainingTicks());
        assertTrue(sink.targetMessages().stream().anyMatch(m -> m.contains("holy light")),
            "Apply message should mention the holy ward");

        CombatModifiers modifiers = new CombatModifierResolver(repository).resolve(player.effects());
        assertTrue(modifiers.defense().add() > 0,
            "A player under Divine Shield must be measurably harder to hit");
    }

    @Test
    void divineShieldExpiresAfterItsDuration() throws Exception {
        EffectRepository repository = new JsonEffectRepository();
        EffectEngine engine = new EffectEngine(repository);
        Player player = paladinPlayer();
        RecordingSink sink = new RecordingSink();

        engine.apply(player, DIVINE_SHIELD_EFFECT, sink);
        for (int tick = 0; tick < 12; tick++) {
            engine.tick(player, sink);
        }

        assertTrue(player.effects().isEmpty(), "Divine Shield must fade after its duration");
        CombatModifiers modifiers = new CombatModifierResolver(repository).resolve(player.effects());
        assertEquals(0, modifiers.defense().add(),
            "Once the ward expires it must no longer improve defense");
    }

    private static Player paladinPlayer() {
        return new Player(
            User.of(Username.of("uther"), Password.hash("pw", 1000)),
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
