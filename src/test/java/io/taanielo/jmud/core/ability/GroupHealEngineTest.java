package io.taanielo.jmud.core.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

/**
 * Unit tests for {@link AbilityTargeting#BENEFICIAL_GROUP} in {@link AbilityEngine}.
 *
 * <p>Verifies that a group-heal ability heals the caster and all room members,
 * deducts mana from the caster, and sets the correct cooldown.
 */
class GroupHealEngineTest {

    private static final AbilityId GROUP_HEAL_ID = AbilityId.of("spell.heal.group");
    private static final int HEAL_AMOUNT = 6;
    private static final int MANA_COST = 8;
    private static final int COOLDOWN_TICKS = 5;

    private TrackingEffectResolver effectResolver;
    private TestCooldowns cooldowns;
    private TestMessageSink messageSink;
    private AbilityEngine engine;

    @BeforeEach
    void setUp() {
        effectResolver = new TrackingEffectResolver();
        cooldowns = new TestCooldowns();
        messageSink = new TestMessageSink();

        Ability groupHeal = groupHealAbility();
        AbilityRegistry registry = new AbilityRegistry(List.of(groupHeal));
        AbilityCostResolver costResolver = new BasicAbilityCostResolver();

        engine = new AbilityEngine(
            registry,
            costResolver,
            effectResolver,
            messageSink,
            // group members resolver: source + companion
            source -> {
                Player companion = makePlayer("companion", 10, 20, 20);
                return List.of(source, companion);
            },
            null
        );
    }

    @Test
    void casterIsHealedByGroupHeal() {
        // Caster has 10/20 HP and 20/20 mana
        Player caster = makePlayer("cleric", 10, 20, 20);
        List<AbilityId> learned = List.of(GROUP_HEAL_ID);

        AbilityUseResult result = engine.use(caster, "group heal", learned,
            (src, inp) -> java.util.Optional.empty(), cooldowns);

        // Caster should be healed (10 + 6 = 16)
        assertEquals(16, result.source().getVitals().hp(),
            "Caster HP should be healed by group heal");
    }

    @Test
    void allRoomMembersAreHealedByGroupHeal() {
        Player caster = makePlayer("cleric", 10, 20, 20);
        List<AbilityId> learned = List.of(GROUP_HEAL_ID);

        AbilityUseResult result = engine.use(caster, "group heal", learned,
            (src, inp) -> java.util.Optional.empty(), cooldowns);

        List<Player> groupTargets = result.groupTargets();
        assertFalse(groupTargets.isEmpty(), "Group targets list must not be empty");

        // Companion (companion) had 10/20 HP, should be at 16/20 after heal
        Player companion = groupTargets.stream()
            .filter(p -> p.getUsername().getValue().equals("companion"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Companion not in group targets"));
        assertEquals(16, companion.getVitals().hp(),
            "Companion HP should be healed by group heal");
    }

    @Test
    void manaIsDeductedFromCasterAfterGroupHeal() {
        Player caster = makePlayer("cleric", 10, 20, 20);
        List<AbilityId> learned = List.of(GROUP_HEAL_ID);

        AbilityUseResult result = engine.use(caster, "group heal", learned,
            (src, inp) -> java.util.Optional.empty(), cooldowns);

        int expectedMana = 20 - MANA_COST;
        assertEquals(expectedMana, result.source().getVitals().mana(),
            "Caster mana should be reduced by the spell cost");
    }

    @Test
    void cooldownIsSetAfterGroupHeal() {
        Player caster = makePlayer("cleric", 10, 20, 20);
        List<AbilityId> learned = List.of(GROUP_HEAL_ID);

        engine.use(caster, "group heal", learned,
            (src, inp) -> java.util.Optional.empty(), cooldowns);

        assertTrue(cooldowns.isOnCooldown(GROUP_HEAL_ID),
            "Group heal must be on cooldown after use");
        assertEquals(COOLDOWN_TICKS, cooldowns.remainingTicks(GROUP_HEAL_ID),
            "Cooldown ticks must match ability definition");
    }

    @Test
    void groupHealFailsWhenOnCooldown() {
        Player caster = makePlayer("cleric", 10, 20, 20);
        List<AbilityId> learned = List.of(GROUP_HEAL_ID);

        // Put ability on cooldown
        cooldowns.startCooldown(GROUP_HEAL_ID, 3);

        AbilityUseResult result = engine.use(caster, "group heal", learned,
            (src, inp) -> java.util.Optional.empty(), cooldowns);

        assertTrue(result.messages().getFirst().contains("cooldown"),
            "Should report cooldown error");
    }

    @Test
    void groupHealFailsWhenInsufficientMana() {
        // Caster has 0 mana
        Player caster = makePlayer("cleric", 10, 0, 20);
        List<AbilityId> learned = List.of(GROUP_HEAL_ID);

        AbilityUseResult result = engine.use(caster, "group heal", learned,
            (src, inp) -> java.util.Optional.empty(), cooldowns);

        assertTrue(result.messages().getFirst().contains("lack the resources"),
            "Should report insufficient resources error");
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static Player makePlayer(String name, int hp, int mana, int maxMana) {
        User user = User.of(Username.of(name), Password.hash("pw", 1));
        PlayerVitals vitals = new PlayerVitals(hp, 20, mana, maxMana, 20, 20);
        return new Player(user, 1, 0, vitals, List.of(), "prompt", false, List.of(), null, null);
    }

    private static Ability groupHealAbility() {
        return new AbilityDefinition(
            GROUP_HEAL_ID,
            "group heal",
            AbilityType.SPELL,
            1,
            new AbilityCost(MANA_COST, 0),
            new AbilityCooldown(COOLDOWN_TICKS),
            AbilityTargeting.BENEFICIAL_GROUP,
            List.of(),
            List.of(new AbilityEffect(
                AbilityEffectKind.VITALS,
                AbilityStat.HP,
                AbilityOperation.INCREASE,
                HEAL_AMOUNT,
                null
            )),
            List.of()
        );
    }

    // ── test doubles ─────────────────────────────────────────────────────

    /** Tracks the heal amount applied per player. */
    private static class TrackingEffectResolver implements AbilityEffectResolver {
        final Map<String, Integer> healedAmounts = new HashMap<>();

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
            Player updatedPlayer = target.withVitals(updated);
            context.updateTarget(updatedPlayer);
            healedAmounts.merge(target.getUsername().getValue(), effect.amount(), Integer::sum);
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
        final List<String> sourceMessages = new ArrayList<>();
        final List<String> roomMessages = new ArrayList<>();

        @Override
        public void sendToSource(Player source, String message) {
            sourceMessages.add(message);
        }

        @Override
        public void sendToTarget(Player target, String message) { }

        @Override
        public void sendToRoom(Player source, Player target, String message) {
            roomMessages.add(message);
        }
    }
}
