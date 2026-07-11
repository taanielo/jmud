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
 * Verifies the Ranger {@code skill.aimed-shot} skill: the JSON loads as a multi-effect
 * {@link AbilityTargeting#HARMFUL} skill, using it deals a direct called-shot hit and applies the
 * new {@code exposed} debuff, and the {@code exposed} effect lowers its victim's effective defense
 * for a few ticks. Also proves the Ranger class grants the skill and that the
 * {@link AbilityEngine} enforces cooldown, cost and targeting constraints — the same guarantees
 * proven for Warrior {@code skill.rend} and Rogue {@code skill.envenom}.
 */
class AimedShotSkillTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final AbilityId AIMED_SHOT = AbilityId.of("skill.aimed-shot");
    private static final EffectId EXPOSED_EFFECT = EffectId.of("exposed");

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
    void aimedShotJsonLoadsAsHarmfulMultiEffectSkill() throws Exception {
        Ability ability = abilityRepository.findById(AIMED_SHOT)
            .orElseThrow(() -> new AssertionError("skill.aimed-shot must be found"));

        assertEquals(AbilityType.SKILL, ability.type());
        assertEquals(1, ability.level());
        assertEquals(AbilityTargeting.HARMFUL, ability.targeting());
        assertTrue(ability.cost().move() > 0, "aimed-shot must cost move like rend");
        assertTrue(ability.cooldown().ticks() > 0, "aimed-shot must have a cooldown");
        assertEquals(2, ability.effects().size(),
            "aimed-shot applies a direct hit plus a debuff effect");

        AbilityEffect vitals = ability.effects().stream()
            .filter(e -> e.kind() == AbilityEffectKind.VITALS)
            .findFirst()
            .orElseThrow(() -> new AssertionError("aimed-shot must have a VITALS damage effect"));
        assertEquals(AbilityStat.HP, vitals.stat());
        assertEquals(AbilityOperation.DECREASE, vitals.operation());
        assertTrue(vitals.amount() > 0);

        AbilityEffect debuff = ability.effects().stream()
            .filter(e -> e.kind() == AbilityEffectKind.EFFECT)
            .findFirst()
            .orElseThrow(() -> new AssertionError("aimed-shot must apply an EFFECT"));
        assertEquals("exposed", debuff.effectId());
    }

    @Test
    void rangerClassGrantsAimedShot() throws Exception {
        JsonClassRepository classRepository = new JsonClassRepository(DATA_ROOT);
        var ranger = classRepository.findById(ClassId.of("ranger"))
            .orElseThrow(() -> new AssertionError("ranger class must be found"));

        assertTrue(ranger.startingAbilityIds().contains(AIMED_SHOT),
            "Ranger ability_ids must include skill.aimed-shot");
    }

    // ── Exposed effect: defense debuff ──────────────────────────────────

    @Test
    void exposedEffectLoadsWithDefenseDebuff() throws Exception {
        EffectDefinition exposed = effectRepository.findById(EXPOSED_EFFECT).orElseThrow();

        assertTrue(exposed.durationTicks() > 0, "exposed must last for a number of ticks");
        assertEquals(EffectStacking.REFRESH, exposed.stacking());

        CombatModifiers modifiers = new CombatModifierResolver(effectRepository)
            .resolve(List.of(EffectInstance.of(EXPOSED_EFFECT, exposed.durationTicks())));
        assertTrue(modifiers.defense().add() < 0,
            "exposed must lower its victim's effective defense while the wound is fresh");
    }

    // ── Multi-effect execution (VITALS + EFFECT in one ability) ─────────

    @Test
    void usingAimedShotDealsDirectDamageAndExposesTarget() throws Exception {
        Ability aimedShot = abilityRepository.findById(AIMED_SHOT).orElseThrow();
        int directDamage = aimedShot.effects().stream()
            .filter(e -> e.kind() == AbilityEffectKind.VITALS)
            .mapToInt(AbilityEffect::amount)
            .sum();

        Player source = fullPlayer("Legolas");
        Player target = fullPlayer("Ogre");
        AbilityEngine engine = engine(aimedShot);
        AbilityTargetResolver resolver = (p, input) -> Optional.of(target);

        AbilityUseResult result = engine.use(
            source, "aimed-shot ogre", List.of(AIMED_SHOT), resolver, recordingCooldowns());

        assertEquals(target.getVitals().hp() - directDamage, result.target().getVitals().hp(),
            "aimed-shot must reduce the target's HP by its direct-hit amount");
        assertTrue(result.target().effects().stream().anyMatch(e -> e.id().equals(EXPOSED_EFFECT)),
            "aimed-shot must leave the target exposed with the shared exposed effect");
    }

    // ── Cooldown / cost gating ──────────────────────────────────────────

    @Test
    void aimedShotIsRejectedWhileOnCooldown() throws Exception {
        Ability aimedShot = abilityRepository.findById(AIMED_SHOT).orElseThrow();
        Player source = fullPlayer("Legolas");
        Player target = fullPlayer("Ogre");
        AbilityEngine engine = engine(aimedShot);
        AbilityTargetResolver resolver = (p, input) -> Optional.of(target);

        AbilityCooldownTracker onCooldown = new AbilityCooldownTracker() {
            @Override public boolean isOnCooldown(AbilityId id) { return true; }
            @Override public int remainingTicks(AbilityId id) { return 3; }
            @Override public void startCooldown(AbilityId id, int ticks) { }
        };

        AbilityUseResult result = engine.use(
            source, "aimed-shot ogre", List.of(AIMED_SHOT), resolver, onCooldown);

        assertTrue(result.messages().stream().anyMatch(m -> m.contains("cooldown")),
            "aimed-shot must be blocked while on cooldown");
    }

    @Test
    void aimedShotIsRejectedWithoutEnoughMove() throws Exception {
        Ability aimedShot = abilityRepository.findById(AIMED_SHOT).orElseThrow();
        Player source = new Player(
            User.of(Username.of("Tired"), Password.hash("pw")),
            1, 0,
            new PlayerVitals(30, 30, 5, 5, 0, 30),
            new ArrayList<>(), "prompt", false, List.of(), null, null);
        Player target = fullPlayer("Ogre");
        AbilityEngine engine = engine(aimedShot);
        AbilityTargetResolver resolver = (p, input) -> Optional.of(target);

        AbilityUseResult result = engine.use(
            source, "aimed-shot ogre", List.of(AIMED_SHOT), resolver, recordingCooldowns());

        assertTrue(result.messages().stream().anyMatch(m -> m.contains("resources")),
            "aimed-shot must be rejected when the source lacks the move cost");
    }

    @Test
    void aimedShotRequiresAnExplicitTarget() throws Exception {
        Ability aimedShot = abilityRepository.findById(AIMED_SHOT).orElseThrow();
        Player source = fullPlayer("Legolas");
        AbilityEngine engine = engine(aimedShot);
        AbilityTargetResolver resolver = (p, input) -> Optional.empty();

        AbilityUseResult result = engine.use(
            source, "aimed-shot", List.of(AIMED_SHOT), resolver, recordingCooldowns());

        assertTrue(result.messages().stream().anyMatch(m -> m.contains("specify a target")),
            "aimed-shot is HARMFUL and must demand an explicit target");
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
