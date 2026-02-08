package io.taanielo.jmud.core.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

class AbilityEngineTest {

    private final TestAbilityMessageSink messageSink = new TestAbilityMessageSink();
    private final AbilityEngine engine = new AbilityEngine(
        testRegistry(),
        new BasicAbilityCostResolver(),
        new TestAbilityEffectResolver(),
        messageSink
    );

    @Test
    void usesHigherLevelAbilityWhenAliasesOverlap() {
        messageSink.clear();
        Player source = Player.of(User.of(Username.of("alice"), Password.hash("pw", 1000)), "prompt", false);
        Player target = Player.of(User.of(Username.of("bob"), Password.hash("pw", 1000)), "prompt", false);
        AbilityTargetResolver resolver = (player, input) -> Optional.of(target);
        TestCooldowns cooldowns = new TestCooldowns();
        List<AbilityId> learned = List.of(AbilityId.of("spell.fireball"), AbilityId.of("spell.fireball.greater"));

        AbilityUseResult result = engine.use(source, "fireball bob", learned, resolver, cooldowns);

        assertEquals("bob", result.target().getUsername().getValue());
        assertEquals(11, result.target().getVitals().hp());
        assertTrue(messageSink.sourceMessages.getFirst().contains("greater fireball"));
    }

    @Test
    void defaultsBeneficialAbilityToSelf() {
        messageSink.clear();
        PlayerVitals vitals = new PlayerVitals(10, 20, 20, 20, 20, 20);
        Player source = new Player(
            User.of(Username.of("healer"), Password.hash("pw", 1000)),
            1,
            0,
            vitals,
            java.util.List.of(),
            "prompt",
            false,
            List.of(AbilityId.of("spell.heal")),
            null,
            null
        );
        AbilityTargetResolver resolver = (player, input) -> Optional.empty();
        TestCooldowns cooldowns = new TestCooldowns();
        List<AbilityId> learned = List.of(AbilityId.of("spell.heal"));

        AbilityUseResult result = engine.use(source, "heal", learned, resolver, cooldowns);

        assertEquals("healer", result.target().getUsername().getValue());
        assertEquals(16, result.target().getVitals().hp());
    }

    @Test
    void requiresTargetForHarmfulAbilities() {
        messageSink.clear();
        Player source = Player.of(User.of(Username.of("alice"), Password.hash("pw", 1000)), "prompt", false);
        AbilityTargetResolver resolver = (player, input) -> Optional.empty();
        TestCooldowns cooldowns = new TestCooldowns();
        List<AbilityId> learned = List.of(AbilityId.of("skill.bash"));

        AbilityUseResult result = engine.use(source, "bash", learned, resolver, cooldowns);

        assertEquals("You must specify a target.", result.messages().getFirst());
    }

    @Test
    void rejectsUnlearnedAbility() {
        messageSink.clear();
        Player source = Player.of(User.of(Username.of("alice"), Password.hash("pw", 1000)), "prompt", false);
        AbilityTargetResolver resolver = (player, input) -> Optional.empty();
        TestCooldowns cooldowns = new TestCooldowns();
        List<AbilityId> learned = List.of();

        AbilityUseResult result = engine.use(source, "bash", learned, resolver, cooldowns);

        assertEquals("You don't know that ability.", result.messages().getFirst());
    }

    @Test
    void enforcesCooldownsAndCosts() {
        messageSink.clear();
        PlayerVitals vitals = new PlayerVitals(20, 20, 10, 20, 20, 20);
        Player source = new Player(
            User.of(Username.of("alice"), Password.hash("pw", 1000)),
            1,
            0,
            vitals,
            java.util.List.of(),
            "prompt",
            false,
            List.of(AbilityId.of("spell.fireball"), AbilityId.of("spell.fireball.greater")),
            null,
            null
        );
        Player target = Player.of(User.of(Username.of("bob"), Password.hash("pw", 1000)), "prompt", false);
        AbilityTargetResolver resolver = (player, input) -> Optional.of(target);
        TestCooldowns cooldowns = new TestCooldowns();
        List<AbilityId> learned = List.of(AbilityId.of("spell.fireball"), AbilityId.of("spell.fireball.greater"));

        AbilityUseResult first = engine.use(source, "fireball bob", learned, resolver, cooldowns);
        AbilityUseResult second = engine.use(first.source(), "fireball bob", learned, resolver, cooldowns);

        assertTrue(messageSink.sourceMessages.getFirst().contains("greater fireball"));
        assertTrue(second.messages().getFirst().contains("cooldown"));
    }

    @Test
    void rejectsUseWhenResourcesAreLow() {
        messageSink.clear();
        PlayerVitals vitals = new PlayerVitals(20, 20, 0, 20, 20, 20);
        Player source = new Player(
            User.of(Username.of("alice"), Password.hash("pw", 1000)),
            1,
            0,
            vitals,
            java.util.List.of(),
            "prompt",
            false,
            List.of(AbilityId.of("spell.fireball"), AbilityId.of("spell.fireball.greater")),
            null,
            null
        );
        Player target = Player.of(User.of(Username.of("bob"), Password.hash("pw", 1000)), "prompt", false);
        AbilityTargetResolver resolver = (player, input) -> Optional.of(target);
        TestCooldowns cooldowns = new TestCooldowns();
        List<AbilityId> learned = List.of(AbilityId.of("spell.fireball"), AbilityId.of("spell.fireball.greater"));

        AbilityUseResult result = engine.use(source, "fireball bob", learned, resolver, cooldowns);

        assertEquals("You lack the resources to use that ability.", result.messages().getFirst());
    }

    private AbilityRegistry testRegistry() {
        Ability bash = new AbilityDefinition(
            AbilityId.of("skill.bash"),
            "bash",
            AbilityType.SKILL,
            1,
            new AbilityCost(0, 3),
            new AbilityCooldown(3),
            AbilityTargeting.HARMFUL,
            List.of(),
            List.of(new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 4, null)),
            null
        );
        Ability fireball = new AbilityDefinition(
            AbilityId.of("spell.fireball"),
            "fireball",
            AbilityType.SPELL,
            1,
            new AbilityCost(3, 0),
            new AbilityCooldown(4),
            AbilityTargeting.HARMFUL,
            List.of(),
            List.of(new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 6, null)),
            null
        );
        Ability greaterFireball = new AbilityDefinition(
            AbilityId.of("spell.fireball.greater"),
            "greater fireball",
            AbilityType.SPELL,
            2,
            new AbilityCost(5, 0),
            new AbilityCooldown(5),
            AbilityTargeting.HARMFUL,
            List.of("fireball"),
            List.of(new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 9, null)),
            null
        );
        Ability heal = new AbilityDefinition(
            AbilityId.of("spell.heal"),
            "heal",
            AbilityType.SPELL,
            1,
            new AbilityCost(4, 0),
            new AbilityCooldown(3),
            AbilityTargeting.BENEFICIAL,
            List.of("healing"),
            List.of(new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.INCREASE, 6, null)),
            null
        );
        return new AbilityRegistry(List.of(bash, fireball, greaterFireball, heal));
    }

    private static class TestCooldowns implements AbilityCooldownTracker {
        private final Map<AbilityId, Integer> cooldowns = new HashMap<>();

        @Override
        public boolean isOnCooldown(AbilityId abilityId) {
            Integer remaining = cooldowns.get(abilityId);
            return remaining != null && remaining > 0;
        }

        @Override
        public int remainingTicks(AbilityId abilityId) {
            return cooldowns.getOrDefault(abilityId, 0);
        }

        @Override
        public void startCooldown(AbilityId abilityId, int ticks) {
            cooldowns.put(abilityId, ticks);
        }
    }

    private static class TestAbilityMessageSink implements AbilityMessageSink {
        private final List<String> sourceMessages = new java.util.ArrayList<>();
        private final List<String> targetMessages = new java.util.ArrayList<>();
        private final List<String> roomMessages = new java.util.ArrayList<>();

        @Override
        public void sendToSource(Player source, String message) {
            sourceMessages.add(message);
        }

        @Override
        public void sendToTarget(Player target, String message) {
            targetMessages.add(message);
        }

        @Override
        public void sendToRoom(Player source, Player target, String message) {
            roomMessages.add(message);
        }

        private void clear() {
            sourceMessages.clear();
            targetMessages.clear();
            roomMessages.clear();
        }
    }

    private static class TestAbilityEffectResolver implements AbilityEffectResolver {
        @Override
        public void apply(AbilityEffect effect, AbilityContext context) {
            if (effect.kind() != AbilityEffectKind.VITALS) {
                return;
            }
            Player target = context.target();
            PlayerVitals vitals = target.getVitals();
            int current = switch (effect.stat()) {
                case HP -> vitals.hp();
                case MANA -> vitals.mana();
                case MOVE -> vitals.move();
            };
            int max = switch (effect.stat()) {
                case HP -> vitals.maxHp();
                case MANA -> vitals.maxMana();
                case MOVE -> vitals.maxMove();
            };
            int next = effect.operation() == AbilityOperation.INCREASE
                ? Math.min(max, current + effect.amount())
                : Math.max(0, current - effect.amount());
            PlayerVitals updated = switch (effect.stat()) {
                case HP -> new PlayerVitals(next, max, vitals.mana(), vitals.maxMana(), vitals.move(), vitals.maxMove());
                case MANA -> new PlayerVitals(vitals.hp(), vitals.maxHp(), next, max, vitals.move(), vitals.maxMove());
                case MOVE -> new PlayerVitals(vitals.hp(), vitals.maxHp(), vitals.mana(), vitals.maxMana(), next, max);
            };
            context.updateTarget(target.withVitals(updated));
        }
    }
}
