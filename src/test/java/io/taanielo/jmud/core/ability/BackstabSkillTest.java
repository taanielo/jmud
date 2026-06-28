package io.taanielo.jmud.core.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.ability.repository.json.JsonAbilityRepository;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.messaging.MessageChannel;
import io.taanielo.jmud.core.messaging.MessagePhase;
import io.taanielo.jmud.core.player.Player;

/**
 * Verifies that the {@code skill.backstab} skill JSON loads correctly and that
 * the {@link AbilityEngine} enforces the first-strike restriction for
 * {@link AbilityTargeting#HARMFUL_OPENER} abilities.
 */
class BackstabSkillTest {

    private static final AbilityId BACKSTAB = AbilityId.of("skill.backstab");

    @TempDir
    Path tempDir;

    // ── JSON loading ────────────────────────────────────────────────────

    @Test
    void backstabJsonLoadsCorrectly() throws Exception {
        Path skillsDir = tempDir.resolve("skills");
        Files.createDirectories(skillsDir);
        Files.writeString(skillsDir.resolve("skill.backstab.json"), backstabJson());

        JsonAbilityRepository repo = new JsonAbilityRepository(tempDir);
        List<Ability> abilities = repo.findAll();

        assertEquals(1, abilities.size());
        Ability ability = abilities.getFirst();

        assertEquals("skill.backstab", ability.id().getValue());
        assertEquals(AbilityType.SKILL, ability.type());
        assertEquals(1, ability.level());
        assertEquals(5, ability.cost().move());
        assertEquals(5, ability.cooldown().ticks());
        assertEquals(AbilityTargeting.HARMFUL_OPENER, ability.targeting());
        assertEquals(1, ability.effects().size());

        AbilityEffect effect = ability.effects().getFirst();
        assertEquals(AbilityEffectKind.VITALS, effect.kind());
        assertEquals(AbilityStat.HP, effect.stat());
        assertEquals(AbilityOperation.DECREASE, effect.operation());
        assertEquals(10, effect.amount());

        long selfMessages = ability.messages().stream()
            .filter(m -> m.phase() == MessagePhase.USE && m.channel() == MessageChannel.SELF)
            .count();
        long targetMessages = ability.messages().stream()
            .filter(m -> m.phase() == MessagePhase.USE && m.channel() == MessageChannel.TARGET)
            .count();
        long roomMessages = ability.messages().stream()
            .filter(m -> m.phase() == MessagePhase.USE && m.channel() == MessageChannel.ROOM)
            .count();
        assertEquals(1, selfMessages, "Expected one SELF message");
        assertEquals(1, targetMessages, "Expected one TARGET message");
        assertEquals(1, roomMessages, "Expected one ROOM message");

        assertTrue(repo.findById(BACKSTAB).isPresent());
    }

    // ── AbilityEngine first-strike enforcement ──────────────────────────

    @Test
    void backstabIsAllowedWhenNotInCombat() {
        Ability backstab = stubBackstab();
        AbilityEngine engine = engineWithAbility(backstab);
        Player source = player("alice");
        Player target = player("bob");
        AbilityTargetResolver resolver = (p, input) -> Optional.of(target);
        AbilityCooldownTracker cooldowns = noopCooldowns();
        List<AbilityId> learned = List.of(BACKSTAB);

        // inCombatCheck always returns false → not in combat → should succeed
        AbilityUseResult result = engine.use(
            source, "backstab bob", learned, resolver, cooldowns, _ -> false
        );

        assertFalse(result.messages().stream().anyMatch(m -> m.contains("opener")),
            "Backstab should not be blocked when source is not in combat");
    }

    @Test
    void backstabIsRejectedWhenAlreadyInCombat() {
        Ability backstab = stubBackstab();
        AbilityEngine engine = engineWithAbility(backstab);
        Player source = player("alice");
        Player target = player("bob");
        AbilityTargetResolver resolver = (p, input) -> Optional.of(target);
        AbilityCooldownTracker cooldowns = noopCooldowns();
        List<AbilityId> learned = List.of(BACKSTAB);

        // inCombatCheck always returns true → already in combat → should be rejected
        AbilityUseResult result = engine.use(
            source, "backstab bob", learned, resolver, cooldowns, _ -> true
        );

        assertTrue(result.messages().stream().anyMatch(m -> m.contains("opener") || m.contains("combat")),
            "Backstab must be rejected when source is already in combat");
    }

    @Test
    void regularHarmfulAbilityIsUnaffectedByCombatCheck() {
        Ability bash = stubBash();
        AbilityEngine engine = engineWithAbility(bash);
        Player source = player("alice");
        Player target = player("bob");
        AbilityTargetResolver resolver = (p, input) -> Optional.of(target);
        AbilityCooldownTracker cooldowns = noopCooldowns();
        List<AbilityId> learned = List.of(AbilityId.of("skill.bash"));

        // inCombatCheck returns true, but bash is HARMFUL (not HARMFUL_OPENER), so it must succeed
        AbilityUseResult result = engine.use(
            source, "bash bob", learned, resolver, cooldowns, _ -> true
        );

        assertFalse(result.messages().stream().anyMatch(m -> m.contains("opener")),
            "Regular HARMFUL abilities must not be blocked by the in-combat check");
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static Player player(String name) {
        return Player.of(User.of(Username.of(name), Password.hash("pw")), "prompt", false);
    }

    private static AbilityEngine engineWithAbility(Ability ability) {
        AbilityRegistry registry = new AbilityRegistry(List.of(ability));
        return new AbilityEngine(
            registry,
            new BasicAbilityCostResolver(),
            new NoopAbilityEffectResolver(),
            new NoopAbilityMessageSink()
        );
    }

    private static AbilityCooldownTracker noopCooldowns() {
        return new AbilityCooldownTracker() {
            @Override public boolean isOnCooldown(AbilityId id) { return false; }
            @Override public int remainingTicks(AbilityId id) { return 0; }
            @Override public void startCooldown(AbilityId id, int ticks) {}
        };
    }

    private static Ability stubBackstab() {
        return new AbilityDefinition(
            BACKSTAB,
            "backstab",
            AbilityType.SKILL,
            1,
            new AbilityCost(0, 5),
            new AbilityCooldown(5),
            AbilityTargeting.HARMFUL_OPENER,
            List.of(),
            List.of(new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 10, null)),
            List.of()
        );
    }

    private static Ability stubBash() {
        return new AbilityDefinition(
            AbilityId.of("skill.bash"),
            "bash",
            AbilityType.SKILL,
            1,
            new AbilityCost(0, 3),
            new AbilityCooldown(3),
            AbilityTargeting.HARMFUL,
            List.of(),
            List.of(new AbilityEffect(AbilityEffectKind.VITALS, AbilityStat.HP, AbilityOperation.DECREASE, 4, null)),
            List.of()
        );
    }

    private static String backstabJson() {
        return """
            {
              "schema_version": 2,
              "id": "skill.backstab",
              "name": "backstab",
              "type": "SKILL",
              "level": 1,
              "cost": {"move": 5},
              "cooldown": {"ticks": 5},
              "targeting": "HARMFUL_OPENER",
              "aliases": [],
              "effects": [
                {"kind": "VITALS", "stat": "HP", "operation": "DECREASE", "amount": 10}
              ],
              "messages": [
                {"phase": "use", "channel": "self", "text": "You slip behind {target} and drive your blade deep!"},
                {"phase": "use", "channel": "target", "text": "{source} backstabs you from the shadows!"},
                {"phase": "use", "channel": "room", "text": "{source} backstabs {target} from the shadows!"}
              ]
            }
            """;
    }

    private static class NoopAbilityEffectResolver implements AbilityEffectResolver {
        @Override
        public void apply(AbilityEffect effect, AbilityContext context) {}
    }

    private static class NoopAbilityMessageSink implements AbilityMessageSink {
        @Override public void sendToSource(Player source, String message) {}
        @Override public void sendToTarget(Player target, String message) {}
        @Override public void sendToRoom(Player source, Player target, String message) {}
    }
}
