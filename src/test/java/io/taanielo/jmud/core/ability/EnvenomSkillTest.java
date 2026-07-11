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
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.repository.json.JsonEffectRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

/**
 * Verifies the Rogue {@code skill.envenom} skill: the JSON loads as a multi-effect
 * {@link AbilityTargeting#HARMFUL} skill, using it deals direct damage and applies the
 * shared {@code poison} effect, and the {@link AbilityEngine} enforces cooldown, cost and
 * targeting constraints. Also proves the poison it inflicts is the same
 * {@link io.taanielo.jmud.core.effects.EffectDefinition} that {@code spell.cure} removes.
 */
class EnvenomSkillTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final AbilityId ENVENOM = AbilityId.of("skill.envenom");
    private static final AbilityId CURE = AbilityId.of("spell.cure");
    private static final EffectId POISON = EffectId.of("poison");

    private JsonAbilityRepository abilityRepository;
    private EffectEngine effectEngine;

    @BeforeEach
    void setUp() throws Exception {
        abilityRepository = new JsonAbilityRepository(DATA_ROOT);
        effectEngine = new EffectEngine(new JsonEffectRepository());
    }

    // ── JSON loading ────────────────────────────────────────────────────

    @Test
    void envenomJsonLoadsAsHarmfulMultiEffectSkill() throws Exception {
        Ability ability = abilityRepository.findById(ENVENOM)
            .orElseThrow(() -> new AssertionError("skill.envenom must be found"));

        assertEquals(AbilityType.SKILL, ability.type());
        assertEquals(1, ability.level());
        assertEquals(AbilityTargeting.HARMFUL, ability.targeting());
        assertTrue(ability.cost().move() > 0, "envenom must cost move like backstab");
        assertTrue(ability.cooldown().ticks() > 0, "envenom must have a cooldown");
        assertEquals(2, ability.effects().size(), "envenom applies a direct hit plus a poison effect");

        AbilityEffect vitals = ability.effects().stream()
            .filter(e -> e.kind() == AbilityEffectKind.VITALS)
            .findFirst()
            .orElseThrow(() -> new AssertionError("envenom must have a VITALS damage effect"));
        assertEquals(AbilityStat.HP, vitals.stat());
        assertEquals(AbilityOperation.DECREASE, vitals.operation());
        assertTrue(vitals.amount() > 0);

        AbilityEffect poison = ability.effects().stream()
            .filter(e -> e.kind() == AbilityEffectKind.EFFECT)
            .findFirst()
            .orElseThrow(() -> new AssertionError("envenom must apply an EFFECT"));
        assertEquals("poison", poison.effectId());
    }

    @Test
    void rogueClassGrantsEnvenom() throws Exception {
        var classRepository = new io.taanielo.jmud.core.character.repository.json.JsonClassRepository(DATA_ROOT);
        var rogue = classRepository.findById(io.taanielo.jmud.core.character.ClassId.of("rogue"))
            .orElseThrow(() -> new AssertionError("rogue class must be found"));

        assertTrue(rogue.startingAbilityIds().contains(ENVENOM),
            "Rogue ability_ids must include skill.envenom");
    }

    // ── Multi-effect execution (VITALS + EFFECT in one ability) ─────────

    @Test
    void usingEnvenomDealsDirectDamageAndPoisonsTarget() throws Exception {
        Ability envenom = abilityRepository.findById(ENVENOM).orElseThrow();
        int directDamage = envenom.effects().stream()
            .filter(e -> e.kind() == AbilityEffectKind.VITALS)
            .mapToInt(AbilityEffect::amount)
            .sum();

        Player source = fullPlayer("Sly");
        Player target = fullPlayer("Ogre");
        AbilityEngine engine = engine(envenom);
        AbilityTargetResolver resolver = (p, input) -> Optional.of(target);

        AbilityUseResult result = engine.use(
            source, "envenom ogre", List.of(ENVENOM), resolver, recordingCooldowns());

        assertEquals(target.getVitals().hp() - directDamage, result.target().getVitals().hp(),
            "envenom must reduce the target's HP by its direct-hit amount");
        assertTrue(result.target().effects().stream().anyMatch(e -> e.id().equals(POISON)),
            "envenom must leave the target poisoned with the shared poison effect");
    }

    // ── Cooldown / cost gating ──────────────────────────────────────────

    @Test
    void envenomIsRejectedWhileOnCooldown() throws Exception {
        Ability envenom = abilityRepository.findById(ENVENOM).orElseThrow();
        Player source = fullPlayer("Sly");
        Player target = fullPlayer("Ogre");
        AbilityEngine engine = engine(envenom);
        AbilityTargetResolver resolver = (p, input) -> Optional.of(target);

        AbilityCooldownTracker onCooldown = new AbilityCooldownTracker() {
            @Override public boolean isOnCooldown(AbilityId id) { return true; }
            @Override public int remainingTicks(AbilityId id) { return 3; }
            @Override public void startCooldown(AbilityId id, int ticks) { }
        };

        AbilityUseResult result = engine.use(
            source, "envenom ogre", List.of(ENVENOM), resolver, onCooldown);

        assertTrue(result.messages().stream().anyMatch(m -> m.contains("cooldown")),
            "envenom must be blocked while on cooldown");
    }

    @Test
    void envenomIsRejectedWithoutEnoughMove() throws Exception {
        Ability envenom = abilityRepository.findById(ENVENOM).orElseThrow();
        Player source = new Player(
            User.of(Username.of("Tired"), Password.hash("pw")),
            1, 0,
            new PlayerVitals(30, 30, 5, 5, 0, 30),
            new ArrayList<>(), "prompt", false, List.of(), null, null);
        Player target = fullPlayer("Ogre");
        AbilityEngine engine = engine(envenom);
        AbilityTargetResolver resolver = (p, input) -> Optional.of(target);

        AbilityUseResult result = engine.use(
            source, "envenom ogre", List.of(ENVENOM), resolver, recordingCooldowns());

        assertTrue(result.messages().stream().anyMatch(m -> m.contains("resources")),
            "envenom must be rejected when the source lacks the move cost");
    }

    @Test
    void envenomRequiresAnExplicitTarget() throws Exception {
        Ability envenom = abilityRepository.findById(ENVENOM).orElseThrow();
        Player source = fullPlayer("Sly");
        AbilityEngine engine = engine(envenom);
        AbilityTargetResolver resolver = (p, input) -> Optional.empty();

        AbilityUseResult result = engine.use(
            source, "envenom", List.of(ENVENOM), resolver, recordingCooldowns());

        assertTrue(result.messages().stream().anyMatch(m -> m.contains("specify a target")),
            "envenom is HARMFUL and must demand an explicit target");
    }

    // ── Cure reuses the same poison EffectDefinition ────────────────────

    @Test
    void spellCureRemovesPoisonInflictedByEnvenom() throws Exception {
        Ability envenom = abilityRepository.findById(ENVENOM).orElseThrow();
        Player source = fullPlayer("Sly");
        Player target = fullPlayer("Ogre");
        AbilityEngine engine = engine(envenom);
        AbilityTargetResolver resolver = (p, input) -> Optional.of(target);

        AbilityUseResult venomResult = engine.use(
            source, "envenom ogre", List.of(ENVENOM), resolver, recordingCooldowns());
        Player poisoned = venomResult.target();
        assertTrue(poisoned.effects().stream().anyMatch(e -> e.id().equals(POISON)),
            "precondition: target must be poisoned");

        // spell.cure removes the effect via the identical EffectEngine path it and mobs share.
        Ability cure = abilityRepository.findById(CURE).orElseThrow();
        AbilityEffect cureEffect = cure.effects().getFirst();
        AbilityContext context = new AbilityContext(source, poisoned);
        DefaultAbilityEffectResolver curer = new DefaultAbilityEffectResolver(
            effectEngine, new CapturingMessageSink(), AbilityEffectListener.noop());

        curer.apply(cureEffect, context);

        assertFalse(context.target().effects().stream().anyMatch(e -> e.id().equals(POISON)),
            "spell.cure must remove the poison applied by envenom");
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
