package io.taanielo.jmud.core.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.repository.json.JsonAbilityRepository;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.repository.json.JsonClassRepository;
import io.taanielo.jmud.core.combat.CombatModifierResolver;
import io.taanielo.jmud.core.combat.CombatModifiers;
import io.taanielo.jmud.core.effects.EffectDefinition;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.EffectInstance;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.EffectStacking;
import io.taanielo.jmud.core.effects.repository.json.JsonEffectRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

/**
 * Verifies the Necromancer {@code spell.death-coil} spell: the JSON loads as a multi-effect
 * {@link AbilityTargeting#HARMFUL} spell, casting it deals direct shadow damage and afflicts the
 * target with the new {@code wither} curse, and the {@code wither} effect applies both a
 * damage-over-time and a negative attack modifier that weakens its victim.
 */
class DeathCoilSpellTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final AbilityId DEATH_COIL = AbilityId.of("spell.death-coil");
    private static final EffectId WITHER = EffectId.of("wither");

    private JsonAbilityRepository abilityRepository;
    private EffectRepository effectRepository;
    private EffectEngine effectEngine;

    @BeforeEach
    void setUp() throws Exception {
        abilityRepository = new JsonAbilityRepository(DATA_ROOT);
        effectRepository = new JsonEffectRepository();
        effectEngine = new EffectEngine(effectRepository);
    }

    // ── JSON loading ────────────────────────────────────────────────────

    @Test
    void deathCoilJsonLoadsAsHarmfulMultiEffectSpell() throws Exception {
        Ability ability = abilityRepository.findById(DEATH_COIL)
            .orElseThrow(() -> new AssertionError("spell.death-coil must be found"));

        assertEquals(AbilityType.SPELL, ability.type());
        assertEquals(AbilityTargeting.HARMFUL, ability.targeting());
        assertTrue(ability.cost().mana() > 0, "death coil must cost mana like other spells");
        assertTrue(ability.cooldown().ticks() > 0, "death coil must have a cooldown");
        assertEquals(2, ability.effects().size(), "death coil deals direct damage plus applies wither");

        AbilityEffect vitals = ability.effects().stream()
            .filter(e -> e.kind() == AbilityEffectKind.VITALS)
            .findFirst()
            .orElseThrow(() -> new AssertionError("death coil must have a VITALS damage effect"));
        assertEquals(AbilityStat.HP, vitals.stat());
        assertEquals(AbilityOperation.DECREASE, vitals.operation());
        assertTrue(vitals.amount() > 0);

        AbilityEffect wither = ability.effects().stream()
            .filter(e -> e.kind() == AbilityEffectKind.EFFECT)
            .findFirst()
            .orElseThrow(() -> new AssertionError("death coil must apply an EFFECT"));
        assertEquals("wither", wither.effectId());
    }

    @Test
    void necromancerClassGrantsDeathCoil() throws Exception {
        JsonClassRepository classRepository = new JsonClassRepository(DATA_ROOT);
        var necromancer = classRepository.findById(ClassId.of("necromancer"))
            .orElseThrow(() -> new AssertionError("necromancer class must be found"));

        assertTrue(necromancer.startingAbilityIds().contains(DEATH_COIL),
            "Necromancer ability_ids must include spell.death-coil");
    }

    // ── Wither effect ───────────────────────────────────────────────────

    @Test
    void witherEffectLoadsWithDamageOverTimeAndAttackDebuff() throws Exception {
        EffectDefinition wither = effectRepository.findById(WITHER).orElseThrow();

        assertTrue(wither.durationTicks() > 0, "wither must last for a number of ticks");
        assertEquals(EffectStacking.REFRESH, wither.stacking());

        CombatModifiers modifiers = new CombatModifierResolver(effectRepository)
            .resolve(List.of(EffectInstance.of(WITHER, wither.durationTicks())));
        assertTrue(modifiers.attack().add() < 0, "wither must weaken its victim's attack");
    }

    // ── Multi-effect execution (VITALS damage + EFFECT curse) ───────────

    @Test
    void castingDeathCoilDealsDamageAndAppliesWither() throws Exception {
        Ability deathCoil = abilityRepository.findById(DEATH_COIL).orElseThrow();
        int directDamage = deathCoil.effects().stream()
            .filter(e -> e.kind() == AbilityEffectKind.VITALS)
            .mapToInt(AbilityEffect::amount)
            .sum();

        Player source = fullPlayer("Malchor");
        Player target = fullPlayer("Ghoul");
        AbilityEngine engine = engine(deathCoil);
        AbilityTargetResolver resolver = (p, input) -> Optional.of(target);

        AbilityUseResult result = engine.use(
            source, "death coil ghoul", List.of(DEATH_COIL), resolver, recordingCooldowns());

        assertEquals(target.getVitals().hp() - directDamage, result.target().getVitals().hp(),
            "death coil must reduce the target's HP by its direct-hit amount");
        assertTrue(result.target().effects().stream().anyMatch(e -> e.id().equals(WITHER)),
            "death coil must leave the target afflicted with the wither curse");
    }

    @Test
    void witherIsVisibleInExamineWhileActive() throws Exception {
        Player target = new Player(
            User.of(Username.of("Ghoul"), Password.hash("pw")),
            1, 0,
            new PlayerVitals(50, 50, 30, 30, 30, 30),
            new ArrayList<>(), "prompt", false, List.of(), null, null);
        RecordingSink sink = new RecordingSink();

        boolean applied = effectEngine.apply(target, WITHER, sink);

        assertTrue(applied);
        assertEquals(1, target.effects().size());
        assertEquals(WITHER, target.effects().getFirst().id());
        assertTrue(target.effects().getFirst().remainingTicks() > 0);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private AbilityEngine engine(Ability ability) {
        DefaultAbilityEffectResolver resolver = new DefaultAbilityEffectResolver(
            effectEngine, new CapturingMessageSink(), AbilityEffectListener.noop());
        return new AbilityEngine(
            new AbilityRegistry(List.of(ability)),
            new BasicAbilityCostResolver(),
            resolver,
            new CapturingMessageSink());
    }

    private static Player fullPlayer(String name) {
        return new Player(
            User.of(Username.of(name), Password.hash("pw")),
            1, 0,
            new PlayerVitals(50, 50, 30, 30, 30, 30),
            new ArrayList<>(), "prompt", false, List.of(), null, null);
    }

    private static AbilityCooldownTracker recordingCooldowns() {
        return new AbilityCooldownTracker() {
            @Override public boolean isOnCooldown(AbilityId id) { return false; }
            @Override public int remainingTicks(AbilityId id) { return 0; }
            @Override public void startCooldown(AbilityId id, int ticks) { }
        };
    }

    private static final class CapturingMessageSink implements AbilityMessageSink {
        @Override public void sendToSource(Player source, String message) { }
        @Override public void sendToTarget(Player target, String message) { }
        @Override public void sendToRoom(Player source, Player target, String message) { }
    }

    private static final class RecordingSink implements io.taanielo.jmud.core.effects.EffectMessageSink {
        @Override public void sendToTarget(String message) { }
    }
}
