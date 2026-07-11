package io.taanielo.jmud.core.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

/**
 * Unit tests for {@link AbilityTargeting#HARMFUL_UNDEAD} in {@link AbilityEngine}.
 *
 * <p>Verifies that turn-undead succeeds against targets with the "undead" tag
 * and is rejected against targets that lack it.
 */
class TurnUndeadEngineTest {

    private static final AbilityId TURN_UNDEAD_ID = AbilityId.of("spell.turn.undead");
    private static final int DAMAGE_AMOUNT = 10;
    private static final int MANA_COST = 5;
    private static final int COOLDOWN_TICKS = 4;

    /** Names that are considered "undead" by the mock checker. */
    private static final Set<String> UNDEAD_NAMES = Set.of("skeleton", "zombie", "crypt-warden");

    private AbilityEngine buildEngine() {
        Ability turnUndead = turnUndeadAbility();
        AbilityRegistry registry = new AbilityRegistry(List.of(turnUndead));
        AbilityCostResolver costResolver = new BasicAbilityCostResolver();
        TestMessageSink messageSink = new TestMessageSink();
        TestEffectResolver effectResolver = new TestEffectResolver();

        // Tag checker: returns true only for known undead names
        AbilityMobTagChecker tagChecker =
            (target, tag) -> "undead".equals(tag)
                && UNDEAD_NAMES.contains(target.getUsername().getValue().toLowerCase(Locale.ROOT));

        return new AbilityEngine(
            registry, costResolver, effectResolver, messageSink, null, tagChecker
        );
    }

    @Test
    void turnUndead_succeedsAgainstUndeadTarget() {
        AbilityEngine engine = buildEngine();
        Player caster = makePlayer("cleric", 20, 20, 20);
        Player skeleton = makePlayer("skeleton", 20, 20, 20);

        AbilityTargetResolver targetResolver = (src, inp) -> Optional.of(skeleton);
        TestCooldowns cooldowns = new TestCooldowns();

        AbilityUseResult result = engine.use(
            caster, "turn undead skeleton",
            List.of(TURN_UNDEAD_ID), targetResolver, cooldowns
        );

        assertFalse(result.messages().isEmpty());
        // Target should have taken damage (20 - 10 = 10)
        assertEquals(10, result.target().getVitals().hp(),
            "Undead target should take damage from turn undead");
    }

    @Test
    void turnUndead_deductsManaOnSuccess() {
        AbilityEngine engine = buildEngine();
        Player caster = makePlayer("cleric", 20, 20, 20);
        Player skeleton = makePlayer("skeleton", 20, 20, 20);

        AbilityTargetResolver targetResolver = (src, inp) -> Optional.of(skeleton);
        TestCooldowns cooldowns = new TestCooldowns();

        AbilityUseResult result = engine.use(
            caster, "turn undead skeleton",
            List.of(TURN_UNDEAD_ID), targetResolver, cooldowns
        );

        int expectedMana = 20 - MANA_COST;
        assertEquals(expectedMana, result.source().getVitals().mana(),
            "Caster mana must be reduced after successful turn undead");
    }

    @Test
    void turnUndead_setsCooldownOnSuccess() {
        AbilityEngine engine = buildEngine();
        Player caster = makePlayer("cleric", 20, 20, 20);
        Player skeleton = makePlayer("skeleton", 20, 20, 20);

        AbilityTargetResolver targetResolver = (src, inp) -> Optional.of(skeleton);
        TestCooldowns cooldowns = new TestCooldowns();

        engine.use(
            caster, "turn undead skeleton",
            List.of(TURN_UNDEAD_ID), targetResolver, cooldowns
        );

        assertTrue(cooldowns.isOnCooldown(TURN_UNDEAD_ID),
            "Turn undead must be on cooldown after use");
        assertEquals(COOLDOWN_TICKS, cooldowns.remainingTicks(TURN_UNDEAD_ID));
    }

    @Test
    void turnUndead_isRejectedAgainstNonUndeadTarget() {
        AbilityEngine engine = buildEngine();
        Player caster = makePlayer("cleric", 20, 20, 20);
        Player goblin = makePlayer("goblin", 20, 20, 20);

        AbilityTargetResolver targetResolver = (src, inp) -> Optional.of(goblin);
        TestCooldowns cooldowns = new TestCooldowns();

        AbilityUseResult result = engine.use(
            caster, "turn undead goblin",
            List.of(TURN_UNDEAD_ID), targetResolver, cooldowns
        );

        String message = result.messages().getFirst();
        assertTrue(message.contains("no effect"),
            "Should report that holy power has no effect: " + message);
        // Target should NOT take damage
        assertEquals(20, goblin.getVitals().hp(),
            "Non-undead target must not take damage");
    }

    @Test
    void turnUndead_requiresExplicitTarget() {
        AbilityEngine engine = buildEngine();
        Player caster = makePlayer("cleric", 20, 20, 20);

        AbilityTargetResolver targetResolver = (src, inp) -> Optional.empty();
        TestCooldowns cooldowns = new TestCooldowns();

        AbilityUseResult result = engine.use(
            caster, "turn undead",
            List.of(TURN_UNDEAD_ID), targetResolver, cooldowns
        );

        String message = result.messages().getFirst();
        assertTrue(message.contains("specify a target"),
            "Should require a target: " + message);
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static Player makePlayer(String name, int hp, int mana, int maxMana) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        PlayerVitals vitals = new PlayerVitals(hp, 20, mana, maxMana, 20, 20);
        return new Player(user, 1, 0, vitals, List.of(), "prompt", false, List.of(), null, null);
    }

    private static Ability turnUndeadAbility() {
        return new AbilityDefinition(
            TURN_UNDEAD_ID,
            "turn undead",
            AbilityType.SPELL,
            1,
            new AbilityCost(MANA_COST, 0),
            new AbilityCooldown(COOLDOWN_TICKS),
            AbilityTargeting.HARMFUL_UNDEAD,
            List.of(),
            List.of(new AbilityEffect(
                AbilityEffectKind.VITALS,
                AbilityStat.HP,
                AbilityOperation.DECREASE,
                DAMAGE_AMOUNT,
                null
            )),
            List.of()
        );
    }

    // ── test doubles ─────────────────────────────────────────────────────

    private static class TestEffectResolver implements AbilityEffectResolver {
        @Override
        public void apply(AbilityEffect effect, AbilityContext context) {
            if (effect.kind() != AbilityEffectKind.VITALS) {
                return;
            }
            Player target = context.target();
            PlayerVitals vitals = target.getVitals();
            PlayerVitals updated = switch (effect.stat()) {
                case HP -> effect.operation() == AbilityOperation.INCREASE
                    ? vitals.heal(effect.amount()) : vitals.damage(effect.amount());
                case MANA -> effect.operation() == AbilityOperation.INCREASE
                    ? vitals.restoreMana(effect.amount()) : vitals.consumeMana(effect.amount());
                case MOVE -> effect.operation() == AbilityOperation.INCREASE
                    ? vitals.restoreMove(effect.amount()) : vitals.consumeMove(effect.amount());
            };
            context.updateTarget(target.withVitals(updated));
        }
    }

    private static class TestCooldowns implements AbilityCooldownTracker {
        private final Map<AbilityId, Integer> cooldowns = new HashMap<>();

        @Override
        public boolean isOnCooldown(AbilityId id) {
            Integer remaining = cooldowns.get(id);
            return remaining != null && remaining > 0;
        }

        @Override
        public int remainingTicks(AbilityId id) {
            return cooldowns.getOrDefault(id, 0);
        }

        @Override
        public void startCooldown(AbilityId id, int ticks) {
            cooldowns.put(id, ticks);
        }
    }

    private static class TestMessageSink implements AbilityMessageSink {
        final List<String> all = new ArrayList<>();

        @Override
        public void sendToSource(Player source, String message) {
            all.add(message);
        }

        @Override
        public void sendToTarget(Player target, String message) {
            all.add(message);
        }

        @Override
        public void sendToRoom(Player source, Player target, String message) {
            all.add(message);
        }
    }
}
