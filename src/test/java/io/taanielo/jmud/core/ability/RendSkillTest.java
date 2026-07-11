package io.taanielo.jmud.core.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Verifies the Warrior {@code skill.rend} skill: the JSON loads as a multi-effect
 * {@link AbilityTargeting#HARMFUL} skill, using it deals a direct wound hit and applies the new
 * {@code rend} bleed effect, and the {@code rend} effect applies both a damage-over-time and a
 * negative {@code defense} modifier that leaves its victim easier to hit while it bleeds. Also
 * proves the bleed it inflicts is the same {@link EffectDefinition} that {@code spell.cure}
 * removes and that the {@link AbilityEngine} enforces cooldown, cost and targeting constraints.
 */
class RendSkillTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final AbilityId REND = AbilityId.of("skill.rend");
    private static final AbilityId CURE = AbilityId.of("spell.cure");
    private static final EffectId REND_EFFECT = EffectId.of("rend");

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
    void rendJsonLoadsAsHarmfulMultiEffectSkill() throws Exception {
        Ability ability = abilityRepository.findById(REND)
            .orElseThrow(() -> new AssertionError("skill.rend must be found"));

        assertEquals(AbilityType.SKILL, ability.type());
        assertEquals(1, ability.level());
        assertEquals(AbilityTargeting.HARMFUL, ability.targeting());
        assertTrue(ability.cost().move() > 0, "rend must cost move like bash");
        assertTrue(ability.cooldown().ticks() > 0, "rend must have a cooldown");
        assertEquals(2, ability.effects().size(), "rend applies a direct hit plus a bleed effect");

        AbilityEffect vitals = ability.effects().stream()
            .filter(e -> e.kind() == AbilityEffectKind.VITALS)
            .findFirst()
            .orElseThrow(() -> new AssertionError("rend must have a VITALS damage effect"));
        assertEquals(AbilityStat.HP, vitals.stat());
        assertEquals(AbilityOperation.DECREASE, vitals.operation());
        assertTrue(vitals.amount() > 0);

        AbilityEffect bleed = ability.effects().stream()
            .filter(e -> e.kind() == AbilityEffectKind.EFFECT)
            .findFirst()
            .orElseThrow(() -> new AssertionError("rend must apply an EFFECT"));
        assertEquals("rend", bleed.effectId());
    }

    @Test
    void warriorClassGrantsRend() throws Exception {
        JsonClassRepository classRepository = new JsonClassRepository(DATA_ROOT);
        var warrior = classRepository.findById(ClassId.of("warrior"))
            .orElseThrow(() -> new AssertionError("warrior class must be found"));

        assertTrue(warrior.startingAbilityIds().contains(REND),
            "Warrior ability_ids must include skill.rend");
    }

    // ── Rend effect: DoT plus defense debuff ────────────────────────────

    @Test
    void rendEffectLoadsWithDamageOverTimeAndDefenseDebuff() throws Exception {
        EffectDefinition rend = effectRepository.findById(REND_EFFECT).orElseThrow();

        assertTrue(rend.durationTicks() > 0, "rend must last for a number of ticks");
        assertEquals(EffectStacking.REFRESH, rend.stacking());

        CombatModifiers modifiers = new CombatModifierResolver(effectRepository)
            .resolve(List.of(EffectInstance.of(REND_EFFECT, rend.durationTicks())));
        assertTrue(modifiers.defense().add() < 0,
            "rend must lower its victim's effective defense while the wound bleeds");
    }

    // ── Multi-effect execution (VITALS + EFFECT in one ability) ─────────

    @Test
    void usingRendDealsDirectDamageAndBleedsTarget() throws Exception {
        Ability rend = abilityRepository.findById(REND).orElseThrow();
        int directDamage = rend.effects().stream()
            .filter(e -> e.kind() == AbilityEffectKind.VITALS)
            .mapToInt(AbilityEffect::amount)
            .sum();

        Player source = fullPlayer("Bruenor");
        Player target = fullPlayer("Ogre");
        AbilityEngine engine = engine(rend);
        AbilityTargetResolver resolver = (p, input) -> Optional.of(target);

        AbilityUseResult result = engine.use(
            source, "rend ogre", List.of(REND), resolver, recordingCooldowns());

        assertEquals(target.getVitals().hp() - directDamage, result.target().getVitals().hp(),
            "rend must reduce the target's HP by its direct-hit amount");
        assertTrue(result.target().effects().stream().anyMatch(e -> e.id().equals(REND_EFFECT)),
            "rend must leave the target bleeding with the shared rend effect");
    }

    // ── Cooldown / cost gating ──────────────────────────────────────────

    @Test
    void rendIsRejectedWhileOnCooldown() throws Exception {
        Ability rend = abilityRepository.findById(REND).orElseThrow();
        Player source = fullPlayer("Bruenor");
        Player target = fullPlayer("Ogre");
        AbilityEngine engine = engine(rend);
        AbilityTargetResolver resolver = (p, input) -> Optional.of(target);

        AbilityCooldownTracker onCooldown = new AbilityCooldownTracker() {
            @Override public boolean isOnCooldown(AbilityId id) { return true; }
            @Override public int remainingTicks(AbilityId id) { return 3; }
            @Override public void startCooldown(AbilityId id, int ticks) { }
        };

        AbilityUseResult result = engine.use(
            source, "rend ogre", List.of(REND), resolver, onCooldown);

        assertTrue(result.messages().stream().anyMatch(m -> m.contains("cooldown")),
            "rend must be blocked while on cooldown");
    }

    @Test
    void rendIsRejectedWithoutEnoughMove() throws Exception {
        Ability rend = abilityRepository.findById(REND).orElseThrow();
        Player source = new Player(
            User.of(Username.of("Tired"), Password.hash("pw")),
            1, 0,
            new PlayerVitals(30, 30, 5, 5, 0, 30),
            new ArrayList<>(), "prompt", false, List.of(), null, null);
        Player target = fullPlayer("Ogre");
        AbilityEngine engine = engine(rend);
        AbilityTargetResolver resolver = (p, input) -> Optional.of(target);

        AbilityUseResult result = engine.use(
            source, "rend ogre", List.of(REND), resolver, recordingCooldowns());

        assertTrue(result.messages().stream().anyMatch(m -> m.contains("resources")),
            "rend must be rejected when the source lacks the move cost");
    }

    @Test
    void rendRequiresAnExplicitTarget() throws Exception {
        Ability rend = abilityRepository.findById(REND).orElseThrow();
        Player source = fullPlayer("Bruenor");
        AbilityEngine engine = engine(rend);
        AbilityTargetResolver resolver = (p, input) -> Optional.empty();

        AbilityUseResult result = engine.use(
            source, "rend", List.of(REND), resolver, recordingCooldowns());

        assertTrue(result.messages().stream().anyMatch(m -> m.contains("specify a target")),
            "rend is HARMFUL and must demand an explicit target");
    }

    // ── Cure reuses the same rend EffectDefinition ──────────────────────

    @Test
    void spellCureRemovesBleedInflictedByRend() throws Exception {
        Ability rend = abilityRepository.findById(REND).orElseThrow();
        Player source = fullPlayer("Bruenor");
        Player target = fullPlayer("Ogre");
        AbilityEngine engine = engine(rend);
        AbilityTargetResolver resolver = (p, input) -> Optional.of(target);

        AbilityUseResult rendResult = engine.use(
            source, "rend ogre", List.of(REND), resolver, recordingCooldowns());
        Player bleeding = rendResult.target();
        assertTrue(bleeding.effects().stream().anyMatch(e -> e.id().equals(REND_EFFECT)),
            "precondition: target must be bleeding");

        // spell.cure removes the effect via the identical EffectEngine path it and mobs share.
        Ability cure = abilityRepository.findById(CURE).orElseThrow();
        AbilityEffect cureEffect = cure.effects().getFirst();
        AbilityContext context = new AbilityContext(source, bleeding);
        DefaultAbilityEffectResolver curer = new DefaultAbilityEffectResolver(
            effectEngine, new CapturingMessageSink(), AbilityEffectListener.noop());

        curer.apply(cureEffect, context);

        assertFalse(context.target().effects().stream().anyMatch(e -> e.id().equals(REND_EFFECT)),
            "spell.cure must remove the bleed applied by rend");
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
}
