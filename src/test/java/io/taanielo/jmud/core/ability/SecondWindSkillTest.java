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
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectRepository;
import io.taanielo.jmud.core.effects.repository.json.JsonEffectRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

/**
 * Verifies the Warrior {@code skill.second-wind} self-sustain skill: the JSON loads as a
 * {@link AbilityTargeting#BENEFICIAL} skill that costs move (not mana) and carries a long
 * emergency cooldown, the Warrior class grants it behind a level gate above one, using it
 * defaults to the caster and restores a meaningful chunk of HP, and the shared
 * {@link AbilityEngine} cost/cooldown path already rejects it when the Warrior is out of move
 * or the skill is still cooling down — no new engine code needed.
 */
class SecondWindSkillTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final AbilityId SECOND_WIND = AbilityId.of("skill.second-wind");

    private JsonAbilityRepository abilityRepository;
    private EffectEngine effectEngine;

    @BeforeEach
    void setUp() throws Exception {
        abilityRepository = new JsonAbilityRepository(DATA_ROOT);
        EffectRepository effectRepository = new JsonEffectRepository();
        effectEngine = new EffectEngine(effectRepository);
    }

    @Test
    void secondWindJsonLoadsAsBeneficialMoveCostSkill() throws Exception {
        Ability ability = abilityRepository.findById(SECOND_WIND)
            .orElseThrow(() -> new AssertionError("skill.second-wind must be found"));

        assertEquals(AbilityType.SKILL, ability.type());
        assertEquals(AbilityTargeting.BENEFICIAL, ability.targeting());
        assertTrue(ability.level() > 1, "second wind must be gated above level 1");
        assertTrue(ability.cost().move() > 0, "second wind must cost move like the other Warrior skills");
        assertEquals(0, ability.cost().mana(), "Warriors have no mana; second wind must not cost mana");
        assertTrue(ability.cooldown().ticks() >= 15 && ability.cooldown().ticks() <= 25,
            "second wind must have a long emergency cooldown (15-25 ticks)");

        assertEquals(1, ability.effects().size());
        AbilityEffect heal = ability.effects().getFirst();
        assertEquals(AbilityEffectKind.VITALS, heal.kind());
        assertEquals(AbilityStat.HP, heal.stat());
        assertEquals(AbilityOperation.INCREASE, heal.operation());
        assertTrue(heal.amount() > 0, "second wind must restore HP");
    }

    @Test
    void warriorClassTrainsSecondWind() throws Exception {
        JsonClassRepository classRepository = new JsonClassRepository(DATA_ROOT);
        var warrior = classRepository.findById(ClassId.of("warrior"))
            .orElseThrow(() -> new AssertionError("warrior class must be found"));

        // Second wind (level 3) is an advanced skill trained at the Master Trainer rather than
        // granted at creation (issue #516).
        assertTrue(warrior.trainableAbilityIds().contains(SECOND_WIND),
            "Warrior trainable_ability_ids must include skill.second-wind");
    }

    @Test
    void usingSecondWindHealsTheWarriorWithNoExplicitTarget() throws Exception {
        Ability ability = abilityRepository.findById(SECOND_WIND).orElseThrow();
        int healAmount = ability.effects().getFirst().amount();

        Player warrior = woundedWarrior("Bruenor");
        int startingHp = warrior.getVitals().hp();
        AbilityEngine engine = engine(ability);
        // No target supplied: a BENEFICIAL ability must default to self.
        AbilityTargetResolver resolver = (p, input) -> Optional.empty();

        AbilityUseResult result = engine.use(
            warrior, "secondwind", List.of(SECOND_WIND), resolver, recordingCooldowns());

        assertEquals("Bruenor", result.target().getUsername().getValue(),
            "second wind must default to the caster");
        assertEquals(startingHp + healAmount, result.target().getVitals().hp(),
            "second wind must restore the Warrior's HP by its heal amount");
    }

    @Test
    void secondWindIsRejectedWhileOnCooldown() throws Exception {
        Ability ability = abilityRepository.findById(SECOND_WIND).orElseThrow();
        Player warrior = woundedWarrior("Bruenor");
        AbilityEngine engine = engine(ability);
        AbilityTargetResolver resolver = (p, input) -> Optional.empty();

        AbilityCooldownTracker onCooldown = new AbilityCooldownTracker() {
            @Override public boolean isOnCooldown(AbilityId id) { return true; }
            @Override public int remainingTicks(AbilityId id) { return 12; }
            @Override public void startCooldown(AbilityId id, int ticks) { }
        };

        AbilityUseResult result = engine.use(
            warrior, "secondwind", List.of(SECOND_WIND), resolver, onCooldown);

        assertTrue(result.messages().stream().anyMatch(m -> m.contains("cooldown")),
            "second wind must be blocked while on cooldown");
    }

    @Test
    void secondWindIsRejectedWithoutEnoughMove() throws Exception {
        Ability ability = abilityRepository.findById(SECOND_WIND).orElseThrow();
        Player exhausted = new Player(
            User.of(Username.of("Tired"), Password.hash("pw")),
            1, 0,
            new PlayerVitals(20, 60, 0, 5, 1, 30),
            new ArrayList<>(), "prompt", false, List.of(), null, null);
        AbilityEngine engine = engine(ability);
        AbilityTargetResolver resolver = (p, input) -> Optional.empty();

        AbilityUseResult result = engine.use(
            exhausted, "secondwind", List.of(SECOND_WIND), resolver, recordingCooldowns());

        assertTrue(result.messages().stream().anyMatch(m -> m.contains("resources")),
            "second wind must be rejected when the Warrior lacks the move cost");
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

    private static Player woundedWarrior(String name) {
        return new Player(
            User.of(Username.of(name), Password.hash("pw")),
            1, 0,
            new PlayerVitals(30, 80, 0, 5, 30, 30),
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
