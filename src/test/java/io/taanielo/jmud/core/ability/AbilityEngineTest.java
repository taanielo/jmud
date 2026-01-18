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

    private final AbilityEngine engine = new AbilityEngine(
        testRegistry(),
        new BasicAbilityCostResolver(),
        new TestAbilityEffectResolver()
    );

    @Test
    void usesHigherLevelAbilityWhenAliasesOverlap() {
        Player source = Player.of(User.of(Username.of("alice"), Password.of("pw")), "prompt", false);
        Player target = Player.of(User.of(Username.of("bob"), Password.of("pw")), "prompt", false);
        AbilityTargetResolver resolver = (player, input) -> Optional.of(target);
        TestCooldowns cooldowns = new TestCooldowns();
        List<String> learned = List.of("spell.fireball", "spell.fireball.greater");

        AbilityUseResult result = engine.use(source, "fireball bob", learned, resolver, cooldowns);

        assertEquals("bob", result.target().getUsername().getValue());
        assertEquals(11, result.target().getVitals().hp());
        assertTrue(result.messages().getFirst().contains("greater fireball"));
    }

    @Test
    void defaultsBeneficialAbilityToSelf() {
        PlayerVitals vitals = new PlayerVitals(10, 20, 20, 20, 20, 20);
        Player source = new Player(
            User.of(Username.of("healer"), Password.of("pw")),
            1,
            0,
            vitals,
            java.util.List.of(),
            "prompt",
            false,
            List.of("spell.heal")
        );
        AbilityTargetResolver resolver = (player, input) -> Optional.empty();
        TestCooldowns cooldowns = new TestCooldowns();
        List<String> learned = List.of("spell.heal");

        AbilityUseResult result = engine.use(source, "heal", learned, resolver, cooldowns);

        assertEquals("healer", result.target().getUsername().getValue());
        assertEquals(16, result.target().getVitals().hp());
    }

    @Test
    void requiresTargetForHarmfulAbilities() {
        Player source = Player.of(User.of(Username.of("alice"), Password.of("pw")), "prompt", false);
        AbilityTargetResolver resolver = (player, input) -> Optional.empty();
        TestCooldowns cooldowns = new TestCooldowns();
        List<String> learned = List.of("skill.bash");

        AbilityUseResult result = engine.use(source, "bash", learned, resolver, cooldowns);

        assertEquals("You must specify a target.", result.messages().getFirst());
    }

    @Test
    void rejectsUnlearnedAbility() {
        Player source = Player.of(User.of(Username.of("alice"), Password.of("pw")), "prompt", false);
        AbilityTargetResolver resolver = (player, input) -> Optional.empty();
        TestCooldowns cooldowns = new TestCooldowns();
        List<String> learned = List.of();

        AbilityUseResult result = engine.use(source, "bash", learned, resolver, cooldowns);

        assertEquals("You don't know that ability.", result.messages().getFirst());
    }

    @Test
    void enforcesCooldownsAndCosts() {
        PlayerVitals vitals = new PlayerVitals(20, 20, 10, 20, 20, 20);
        Player source = new Player(
            User.of(Username.of("alice"), Password.of("pw")),
            1,
            0,
            vitals,
            java.util.List.of(),
            "prompt",
            false,
            List.of("spell.fireball", "spell.fireball.greater")
        );
        Player target = Player.of(User.of(Username.of("bob"), Password.of("pw")), "prompt", false);
        AbilityTargetResolver resolver = (player, input) -> Optional.of(target);
        TestCooldowns cooldowns = new TestCooldowns();
        List<String> learned = List.of("spell.fireball", "spell.fireball.greater");

        AbilityUseResult first = engine.use(source, "fireball bob", learned, resolver, cooldowns);
        AbilityUseResult second = engine.use(first.source(), "fireball bob", learned, resolver, cooldowns);

        assertTrue(first.messages().getFirst().contains("greater fireball"));
        assertTrue(second.messages().getFirst().contains("cooldown"));
    }

    @Test
    void rejectsUseWhenResourcesAreLow() {
        PlayerVitals vitals = new PlayerVitals(20, 20, 0, 20, 20, 20);
        Player source = new Player(
            User.of(Username.of("alice"), Password.of("pw")),
            1,
            0,
            vitals,
            java.util.List.of(),
            "prompt",
            false,
            List.of("spell.fireball", "spell.fireball.greater")
        );
        Player target = Player.of(User.of(Username.of("bob"), Password.of("pw")), "prompt", false);
        AbilityTargetResolver resolver = (player, input) -> Optional.of(target);
        TestCooldowns cooldowns = new TestCooldowns();
        List<String> learned = List.of("spell.fireball", "spell.fireball.greater");

        AbilityUseResult result = engine.use(source, "fireball bob", learned, resolver, cooldowns);

        assertEquals("You lack the resources to use that ability.", result.messages().getFirst());
    }

    private AbilityRegistry testRegistry() {
        Ability bash = new AbilityDefinition(
            "skill.bash",
            "bash",
            AbilityType.SKILL,
            1,
            new AbilityCost(0, 3),
            new AbilityCooldown(3),
            AbilityTargeting.HARMFUL,
            List.of(),
            List.of(new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 4, null))
        );
        Ability fireball = new AbilityDefinition(
            "spell.fireball",
            "fireball",
            AbilityType.SPELL,
            1,
            new AbilityCost(3, 0),
            new AbilityCooldown(4),
            AbilityTargeting.HARMFUL,
            List.of(),
            List.of(new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 6, null))
        );
        Ability greaterFireball = new AbilityDefinition(
            "spell.fireball.greater",
            "greater fireball",
            AbilityType.SPELL,
            2,
            new AbilityCost(5, 0),
            new AbilityCooldown(5),
            AbilityTargeting.HARMFUL,
            List.of("fireball"),
            List.of(new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 9, null))
        );
        Ability heal = new AbilityDefinition(
            "spell.heal",
            "heal",
            AbilityType.SPELL,
            1,
            new AbilityCost(4, 0),
            new AbilityCooldown(3),
            AbilityTargeting.BENEFICIAL,
            List.of("healing"),
            List.of(new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.INCREASE, 6, null))
        );
        return new AbilityRegistry(List.of(bash, fireball, greaterFireball, heal));
    }

    private static class TestCooldowns implements AbilityCooldownTracker {
        private final Map<String, Integer> cooldowns = new HashMap<>();

        @Override
        public boolean isOnCooldown(String abilityId) {
            Integer remaining = cooldowns.get(abilityId);
            return remaining != null && remaining > 0;
        }

        @Override
        public int remainingTicks(String abilityId) {
            return cooldowns.getOrDefault(abilityId, 0);
        }

        @Override
        public void startCooldown(String abilityId, int ticks) {
            cooldowns.put(abilityId, ticks);
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
