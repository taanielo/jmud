package io.taanielo.jmud.core.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;

class AbilityEngineTest {

    private final AbilityEngine engine = new AbilityEngine(AbilityCatalog.defaultRegistry());

    @Test
    void usesHigherLevelAbilityWhenAliasesOverlap() {
        Player source = Player.of(User.of(Username.of("alice"), Password.of("pw")), "prompt", false);
        Player target = Player.of(User.of(Username.of("bob"), Password.of("pw")), "prompt", false);
        AbilityTargetResolver resolver = (player, input) -> Optional.of(target);
        TestCooldowns cooldowns = new TestCooldowns();

        AbilityUseResult result = engine.use(source, "fireball bob", resolver, cooldowns);

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
            false
        );
        AbilityTargetResolver resolver = (player, input) -> Optional.empty();
        TestCooldowns cooldowns = new TestCooldowns();

        AbilityUseResult result = engine.use(source, "heal", resolver, cooldowns);

        assertEquals("healer", result.target().getUsername().getValue());
        assertEquals(16, result.target().getVitals().hp());
    }

    @Test
    void requiresTargetForHarmfulAbilities() {
        Player source = Player.of(User.of(Username.of("alice"), Password.of("pw")), "prompt", false);
        AbilityTargetResolver resolver = (player, input) -> Optional.empty();
        TestCooldowns cooldowns = new TestCooldowns();

        AbilityUseResult result = engine.use(source, "bash", resolver, cooldowns);

        assertEquals("You must specify a target.", result.messages().getFirst());
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
            false
        );
        Player target = Player.of(User.of(Username.of("bob"), Password.of("pw")), "prompt", false);
        AbilityTargetResolver resolver = (player, input) -> Optional.of(target);
        TestCooldowns cooldowns = new TestCooldowns();

        AbilityUseResult first = engine.use(source, "fireball bob", resolver, cooldowns);
        AbilityUseResult second = engine.use(first.source(), "fireball bob", resolver, cooldowns);

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
            false
        );
        Player target = Player.of(User.of(Username.of("bob"), Password.of("pw")), "prompt", false);
        AbilityTargetResolver resolver = (player, input) -> Optional.of(target);
        TestCooldowns cooldowns = new TestCooldowns();

        AbilityUseResult result = engine.use(source, "fireball bob", resolver, cooldowns);

        assertEquals("You lack the resources to use that ability.", result.messages().getFirst());
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
}
